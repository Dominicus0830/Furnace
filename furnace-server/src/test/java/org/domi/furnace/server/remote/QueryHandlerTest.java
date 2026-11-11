package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

/** 바닐라 QueryThreadGs4 패킷 형식을 바이트로 검증한다(시계·난수 주입). */
class QueryHandlerTest {

    private static final SocketAddress CLIENT = new InetSocketAddress("127.0.0.1", 50000);
    private static final byte[] SID = {1, 2, 3, 4};

    private final AtomicLong now = new AtomicLong(1_000);
    private int token = 123456;
    private String[] names = {"Alex", "Steve"};

    private final QueryHandler.Info info = new QueryHandler.Info() {
        @Override public String motd() { return "A Minecraft Server"; }
        @Override public String levelName() { return "world"; }
        @Override public String version() { return "1.21.1"; }
        @Override public String plugins() { return ""; }
        @Override public int playerCount() { return names.length; }
        @Override public int maxPlayers() { return 20; }
        @Override public int hostPort() { return 25565; }
        @Override public String hostIp() { return "10.0.0.5"; }
        @Override public String[] playerNames() { return names; }
    };

    private QueryHandler handler() { return new QueryHandler(info, now::get, () -> token); }

    private static byte[] handshakeReq() { return new byte[] {(byte) 0xFE, (byte) 0xFD, 9, 1, 2, 3, 4}; }

    private static byte[] statReq(int token, boolean full) {
        byte[] b = new byte[full ? 15 : 11];
        b[0] = (byte) 0xFE;
        b[1] = (byte) 0xFD;
        b[2] = 0;
        System.arraycopy(SID, 0, b, 3, 4);
        b[7] = (byte) (token >>> 24);
        b[8] = (byte) (token >>> 16);
        b[9] = (byte) (token >>> 8);
        b[10] = (byte) token;
        return b;
    }

    /** 각 문자열 뒤에 NUL 을 붙여 잇는다(리터럴 "\0" 뒤에 숫자가 오면 8진수 이스케이프가 되므로 쓰지 않는다). */
    private static String z(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) sb.append(p).append('\u0000');
        return sb.toString();
    }

    private static String s(byte[] b, int from, int to) { return new String(b, from, to - from, StandardCharsets.ISO_8859_1); }

    @Test
    void handshakeReturnsTabSessionIdTokenNul() {
        byte[] r = handler().handle(CLIENT, handshakeReq(), 7);
        assertArrayEquals(("\t" + new String(SID, StandardCharsets.UTF_8) + "123456" + '\u0000').getBytes(StandardCharsets.UTF_8), r);
    }

    @Test
    void basicStatLayout() {
        QueryHandler h = handler();
        h.handle(CLIENT, handshakeReq(), 7);
        byte[] r = h.handle(CLIENT, statReq(token, false), 11);
        assertNotNull(r);
        assertEquals(0, r[0]);
        assertArrayEquals(SID, new byte[] {r[1], r[2], r[3], r[4]});
        // motd, SMP, map, numplayers, maxplayers 는 NUL 로 끝나는 문자열, 그 뒤 포트(LE short), hostip
        String head = z("A Minecraft Server", "SMP", "world", "2", "20");
        assertEquals(head, s(r, 5, 5 + head.length()));
        int portAt = 5 + head.length();
        assertEquals(25565, (r[portAt] & 0xFF) | (r[portAt + 1] & 0xFF) << 8);
        assertEquals(z("10.0.0.5"), s(r, portAt + 2, r.length));
    }

    @Test
    void fullStatLayout() {
        QueryHandler h = handler();
        h.handle(CLIENT, handshakeReq(), 7);
        byte[] r = h.handle(CLIENT, statReq(token, true), 15);
        assertEquals(0, r[0]);
        assertArrayEquals(SID, new byte[] {r[1], r[2], r[3], r[4]});
        String body = s(r, 5, r.length);
        assertEquals(z("splitnum") + "\u0080\u0000" + z("hostname", "A Minecraft Server", "gametype", "SMP", "game_id", "MINECRAFT", "version", "1.21.1",
                "plugins", "", "map", "world", "numplayers", "2", "maxplayers", "20", "hostport", "25565", "hostip", "10.0.0.5")
                + "\u0000\u0001" + z("player_") + "\u0000" + z("Alex", "Steve") + "\u0000", body);
    }

    @Test
    void fullStatEmptyPlayerList() {
        names = new String[0];
        QueryHandler h = handler();
        h.handle(CLIENT, handshakeReq(), 7);
        byte[] r = h.handle(CLIENT, statReq(token, true), 15);
        assertEquals("\u0001" + z("player_") + "\u0000\u0000", s(r, r.length - 11, r.length));
    }

    @Test
    void invalidOrMissingChallengeGetsNoReply() {
        QueryHandler h = handler();
        assertNull(h.handle(CLIENT, statReq(token, false), 11));   // 핸드셰이크 없이
        h.handle(CLIENT, handshakeReq(), 7);
        assertNull(h.handle(CLIENT, statReq(token + 1, false), 11));
        assertNotNull(h.handle(CLIENT, statReq(token, false), 11));
        assertNull(h.handle(new InetSocketAddress("127.0.0.1", 50001), statReq(token, false), 11));   // 다른 주소의 토큰은 안 통한다
    }

    @Test
    void garbageIsIgnored() {
        QueryHandler h = handler();
        assertNull(h.handle(CLIENT, new byte[] {1, 2}, 2));
        assertNull(h.handle(CLIENT, new byte[] {0, 0, 9, 1, 2, 3, 4}, 7));   // 매직 불일치
        assertNull(h.handle(CLIENT, new byte[] {(byte) 0xFE, (byte) 0xFD, 5, 1, 2, 3, 4}, 7));   // 모르는 종류
        assertNull(h.handle(CLIENT, new byte[] {(byte) 0xFE, (byte) 0xFD, 9}, 3));   // 짧은 핸드셰이크
    }

    @Test
    void fullStatIsCachedForFiveSecondsWithSessionIdPatched() {
        QueryHandler h = handler();
        h.handle(CLIENT, handshakeReq(), 7);
        byte[] first = h.handle(CLIENT, statReq(token, true), 15);
        names = new String[] {"Late"};
        now.addAndGet(4_999);
        byte[] cached = h.handle(CLIENT, statReq(token, true), 15);
        assertArrayEquals(first, cached);   // 5초 안에는 접속자가 바뀌어도 같다
        // 다른 세션 id 의 클라이언트는 캐시 본문에 자기 세션 id 만 갈아 끼운다
        SocketAddress other = new InetSocketAddress("127.0.0.1", 50002);
        h.handle(other, new byte[] {(byte) 0xFE, (byte) 0xFD, 9, 9, 8, 7, 6}, 7);
        byte[] req = statReq(token, true);
        req[3] = 9;
        req[4] = 8;
        req[5] = 7;
        req[6] = 6;
        byte[] r2 = h.handle(other, req, 15);
        assertArrayEquals(new byte[] {9, 8, 7, 6}, new byte[] {r2[1], r2[2], r2[3], r2[4]});
        assertArrayEquals(java.util.Arrays.copyOfRange(first, 5, first.length), java.util.Arrays.copyOfRange(r2, 5, r2.length));
        now.addAndGet(2);   // 5초 지남 → 다시 만든다
        byte[] fresh = h.handle(CLIENT, statReq(token, true), 15);
        assertEquals(true, s(fresh, 0, fresh.length).contains(z("numplayers", "1")));
    }

    @Test
    void challengesAreClearedEveryThirtySeconds() {
        QueryHandler h = handler();
        h.handle(CLIENT, handshakeReq(), 7);
        assertEquals(1, h.challengeCount());
        now.addAndGet(29_999);
        h.prune();
        assertEquals(1, h.challengeCount());
        now.addAndGet(2);
        h.prune();
        assertEquals(0, h.challengeCount());
        assertNull(h.handle(CLIENT, statReq(token, false), 11));
    }

    @Test
    void nonLatin1MotdIsTruncatedToLowByteLikeVanilla() {
        QueryHandler h = new QueryHandler(new QueryHandler.Info() {
            @Override public String motd() { return "가A"; }   // 가 = 0xAC00 → 하위 바이트 0x00
            @Override public String levelName() { return "w"; }
            @Override public String version() { return "v"; }
            @Override public String plugins() { return ""; }
            @Override public int playerCount() { return 0; }
            @Override public int maxPlayers() { return 1; }
            @Override public int hostPort() { return 1; }
            @Override public String hostIp() { return "h"; }
            @Override public String[] playerNames() { return new String[0]; }
        }, now::get, () -> token);
        h.handle(CLIENT, handshakeReq(), 7);
        byte[] r = h.handle(CLIENT, statReq(token, false), 11);
        assertEquals(0, r[5]);   // 글자 하나가 바이트 하나(하위 8비트)
        assertEquals('A', r[6]);
    }

    @Test
    void tokensFromRealRandomFitIn24Bits() {
        List<Integer> seen = new ArrayList<>();
        java.util.Random rnd = new java.util.Random(7);
        QueryHandler h = new QueryHandler(info, now::get, () -> rnd.nextInt(1 << 24));
        for (int i = 0; i < 50; i++) {
            byte[] r = h.handle(new InetSocketAddress("127.0.0.1", 40000 + i), handshakeReq(), 7);
            String t = new String(r, 5, r.length - 6, StandardCharsets.UTF_8);
            seen.add(Integer.parseInt(t));
        }
        for (int v : seen) assertEquals(true, v >= 0 && v < (1 << 24));
    }

    @Test
    void challengeMapIsCappedByDroppingOldest() {
        QueryHandler h = handler();
        for (int i = 0; i < QueryHandler.MAX_CHALLENGES; i++) {
            assertNotNull(h.handle(new InetSocketAddress("10.0." + (i / 8) + "." + (i % 8), 1000), handshakeReq(), 7));
        }
        assertEquals(QueryHandler.MAX_CHALLENGES, h.challengeCount());
        assertNotNull(h.handle(new InetSocketAddress("10.9.9.9", 5), handshakeReq(), 7));   // 가득 차도 새 핸드셰이크를 받는다
        assertEquals(QueryHandler.MAX_CHALLENGES, h.challengeCount());
        assertNull(h.handle(new InetSocketAddress("10.0.0.0", 1000), statReq(token, false), 11));   // 가장 오래된 것이 지워졌다
        assertNotNull(h.handle(new InetSocketAddress("10.0.0.1", 1000), statReq(token, false), 11));
    }

    @Test
    void oneAddressCannotFillTableByVaryingPorts() {
        // A02-03 재현: 한 IP 가 원본 포트 1~1024 로 핸드셰이크해도 그 IP 몫은 상한까지만, 다른 클라이언트의 질의는 막히지 않는다.
        QueryHandler h = handler();
        for (int port = 1; port <= QueryHandler.MAX_CHALLENGES; port++) {
            assertNotNull(h.handle(new InetSocketAddress("203.0.113.7", port), handshakeReq(), 7));
        }
        assertEquals(QueryHandler.MAX_CHALLENGES_PER_IP, h.challengeCount());
        now.addAndGet(29_000);
        h.prune();
        InetSocketAddress legit = new InetSocketAddress("198.51.100.20", 40000);
        assertNotNull(h.handle(legit, handshakeReq(), 7));
        assertNotNull(h.handle(legit, statReq(token, false), 11));
        assertNotNull(h.handle(new InetSocketAddress("203.0.113.7", QueryHandler.MAX_CHALLENGES), statReq(token, false), 11));   // 그 IP 의 최근 것은 남는다
        assertNull(h.handle(new InetSocketAddress("203.0.113.7", 1), statReq(token, false), 11));
    }

    @Test
    void rehandshakeRefreshesWithoutGrowing() {
        QueryHandler h = handler();
        for (int i = 0; i < 3; i++) assertNotNull(h.handle(CLIENT, handshakeReq(), 7));
        assertEquals(1, h.challengeCount());
        now.addAndGet(30_001);
        h.prune();
        assertEquals(0, h.challengeCount());
        for (int p = 0; p < QueryHandler.MAX_CHALLENGES_PER_IP; p++) h.handle(new InetSocketAddress("10.1.1.1", 100 + p), handshakeReq(), 7);
        assertEquals(QueryHandler.MAX_CHALLENGES_PER_IP, h.challengeCount());   // prune 뒤 IP 몫이 다시 비어 있다
    }
}
