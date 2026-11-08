// 바닐라 QueryThreadGs4 의 패킷 처리(GameSpy4 Query). 소켓과 분리한 순수 상태 기계라 시험에서 바이트로 직접 검증한다.
// 요청: FE FD <종류> <세션 id 4바이트> [챌린지 토큰 int(BE)] [패딩 4바이트]. 종류 9 = 핸드셰이크, 0 = 상태(15바이트면 full, 아니면 basic).
// 바닐라 특이점을 그대로 둔다: 문자열은 DataOutputStream.writeBytes(글자의 하위 8비트만), 세션 id 는 문자열로 한 번 변환해 돌려준다,
// 챌린지는 30초마다 통째로 비운다, full 상태는 5초 캐시.
package org.domi.furnace.server.remote;

import java.io.ByteArrayOutputStream;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

public final class QueryHandler {

    /** 상태 응답에 쓰는 서버 정보. 질의 IO 스레드에서 불리므로 스레드 안전해야 한다. */
    public interface Info {
        String motd();
        String levelName();
        String version();
        /** 바닐라는 항상 빈 문자열. */
        String plugins();
        int playerCount();
        int maxPlayers();
        /** 게임 포트(질의 포트가 아님 — 바닐라와 같다). */
        int hostPort();
        String hostIp();
        String[] playerNames();
    }

    static final long CLEAN_INTERVAL_MS = 30_000L;
    /**
     * 보안 보강(바닐라에는 없음, 바닐라는 30초 동안 한없이 쌓는다): 챌린지 맵 크기 상한. 가득 차면 가장 오래된 챌린지를 지우고 새 핸드셰이크를 받는다
     * — 거절하면 한 주소가 원본 포트만 바꿔 표를 채워 정상 질의를 30초 막을 수 있었다(A02-03).
     */
    public static final int MAX_CHALLENGES = 1024;
    /** 한 IP 가 동시에 가질 수 있는 챌린지 수(원본 포트별). 넘으면 그 IP 의 가장 오래된 것을 지운다. */
    public static final int MAX_CHALLENGES_PER_IP = 8;
    static final long FULL_CACHE_MS = 5_000L;

    private record Challenge(long createdAt, int token, byte[] sessionId, byte[] reply) {}

    private final Info info;
    private final LongSupplier clock;
    private final IntSupplier tokens;
    /** 넣은 순서 = 오래된 순서(갱신할 때는 지우고 다시 넣는다). */
    private final Map<SocketAddress, Challenge> challenges = new LinkedHashMap<>();
    private final Map<Object, Integer> perIp = new HashMap<>();
    private long lastClean;
    private long lastFull;
    private byte[] fullCache;

    /** tokens: 0 이상 2^24 미만의 난수. clock: 밀리초. */
    public QueryHandler(Info info, LongSupplier clock, IntSupplier tokens) {
        this.info = info;
        this.clock = clock;
        this.tokens = tokens;
        this.lastClean = clock.getAsLong();
    }

    /** 주기 청소(바닐라 pruneChallenges): 30초마다 만들어진 지 지난 챌린지를 모두 지운다. */
    public synchronized void prune() {
        long now = clock.getAsLong();
        if (now >= lastClean + CLEAN_INTERVAL_MS) {
            lastClean = now;
            for (Iterator<Map.Entry<SocketAddress, Challenge>> it = challenges.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<SocketAddress, Challenge> e = it.next();
                if (e.getValue().createdAt < now) {
                    it.remove();
                    dropIp(e.getKey());
                }
            }
        }
    }

    private static Object ipOf(SocketAddress a) {
        return a instanceof InetSocketAddress isa && isa.getAddress() != null ? isa.getAddress() : a;
    }

    private void dropIp(SocketAddress a) {
        perIp.computeIfPresent(ipOf(a), (k, n) -> n <= 1 ? null : n - 1);
    }

    private void remove(SocketAddress a) {
        if (challenges.remove(a) != null) dropIp(a);
    }

    /** 표에 자리를 낸다: 같은 IP 가 상한이면 그 IP 의 가장 오래된 것, 전체가 차면 가장 오래된 것을 지운다. */
    private void makeRoom(SocketAddress from) {
        Object ip = ipOf(from);
        if (perIp.getOrDefault(ip, 0) >= MAX_CHALLENGES_PER_IP) {
            for (SocketAddress a : challenges.keySet()) {
                if (ipOf(a).equals(ip)) {
                    remove(a);
                    break;
                }
            }
        }
        if (challenges.size() >= MAX_CHALLENGES) remove(challenges.keySet().iterator().next());
    }

    public synchronized int challengeCount() { return challenges.size(); }

    /** 패킷 하나를 처리해 보낼 응답을 돌려준다(응답이 없으면 null). */
    public synchronized byte[] handle(SocketAddress from, byte[] data, int len) {
        if (len < 3 || data[0] != (byte) 0xFE || data[1] != (byte) 0xFD) return null;
        switch (data[2]) {
            case 9 -> {
                return handshake(from, data, len);
            }
            case 0 -> {
                if (!validChallenge(from, data, len)) return null;
                return len == 15 ? fullStat(from) : basicStat(from);
            }
            default -> {
                return null;
            }
        }
    }

    private byte[] handshake(SocketAddress from, byte[] data, int len) {
        if (len < 7) return null;   // 바닐라는 버퍼가 1460 이라 예외 없이 쓰레기를 읽는다 — 짧은 패킷은 버린다
        byte[] sid = {data[3], data[4], data[5], data[6]};
        remove(from);   // 같은 주소의 다시 핸드셰이크는 갱신(맨 뒤로)
        makeRoom(from);
        int token = tokens.getAsInt();
        String sidText = new String(sid, StandardCharsets.UTF_8);
        byte[] reply = String.format(Locale.ROOT, "\t%s%d\u0000", sidText, token).getBytes(StandardCharsets.UTF_8);
        challenges.put(from, new Challenge(clock.getAsLong(), token, sid, reply));
        perIp.merge(ipOf(from), 1, Integer::sum);
        return reply;
    }

    private boolean validChallenge(SocketAddress from, byte[] data, int len) {
        Challenge c = challenges.get(from);
        if (c == null) return false;
        return c.token == readIntBE(data, 7, len);
    }

    /** 바닐라 ave.c: 4바이트가 안 남았으면 0. */
    static int readIntBE(byte[] b, int off, int len) {
        return len - off - 4 < 0 ? 0 : b[off] << 24 | (b[off + 1] & 0xFF) << 16 | (b[off + 2] & 0xFF) << 8 | b[off + 3] & 0xFF;
    }

    private byte[] basicStat(SocketAddress from) {
        Out o = new Out();
        o.b(0);
        o.raw(challenges.get(from).sessionId);
        o.str(info.motd());
        o.str("SMP");
        o.str(info.levelName());
        o.str(Integer.toString(info.playerCount()));
        o.str(Integer.toString(info.maxPlayers()));
        o.shortLE((short) info.hostPort());
        o.str(info.hostIp());
        return o.bytes();
    }

    private byte[] fullStat(SocketAddress from) {
        long now = clock.getAsLong();
        byte[] sid = challenges.get(from).sessionId;
        if (fullCache != null && now < lastFull + FULL_CACHE_MS) {
            byte[] c = fullCache.clone();
            System.arraycopy(sid, 0, c, 1, 4);   // 캐시에는 세션 id 자리만 갈아 끼운다
            return c;
        }
        lastFull = now;
        Out o = new Out();
        o.b(0);
        o.raw(sid);
        o.str("splitnum");
        o.b(128);
        o.b(0);
        o.str("hostname");
        o.str(info.motd());
        o.str("gametype");
        o.str("SMP");
        o.str("game_id");
        o.str("MINECRAFT");
        o.str("version");
        o.str(info.version());
        o.str("plugins");
        o.str(info.plugins());
        o.str("map");
        o.str(info.levelName());
        o.str("numplayers");
        o.str(Integer.toString(info.playerCount()));
        o.str("maxplayers");
        o.str(Integer.toString(info.maxPlayers()));
        o.str("hostport");
        o.str(Integer.toString(info.hostPort()));
        o.str("hostip");
        o.str(info.hostIp());
        o.b(0);
        o.b(1);
        o.str("player_");
        o.b(0);
        for (String n : info.playerNames()) o.str(n);
        o.b(0);
        fullCache = o.bytes();
        return fullCache.clone();
    }

    /** DataOutputStream 흉내: writeBytes 는 글자의 하위 8비트만 쓴다. */
    private static final class Out {
        private final ByteArrayOutputStream bos = new ByteArrayOutputStream(1460);

        void b(int v) { bos.write(v); }

        void raw(byte[] v) { bos.write(v, 0, v.length); }

        void str(String s) {
            for (int i = 0; i < s.length(); i++) bos.write((byte) s.charAt(i));
            bos.write(0);
        }

        void shortLE(short v) {
            bos.write(v & 0xFF);
            bos.write(v >> 8 & 0xFF);
        }

        byte[] bytes() { return bos.toByteArray(); }
    }
}
