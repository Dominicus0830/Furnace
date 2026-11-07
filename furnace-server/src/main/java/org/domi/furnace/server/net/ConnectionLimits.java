// 접속 방어 설정: 단계별 패킷 속도·프레임 크기·시간 초과, 채팅·명령·아이템 길이 상한, PROXY 프로토콜.
// 기본값은 전부 지금까지의 동작(= 바닐라와 같은 결과)이다. 바닐라보다 조이는 값은 furnace.properties 에서 직접 켠다.
package org.domi.furnace.server.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;
import org.domi.furnace.server.net.protocol.ConnectionState;

public final class ConnectionLimits {

    /** 프레임 하드 상한 — 바닐라 21비트 길이(2 MiB). FrameDecoder 도 같은 값으로 자른다. */
    public static final int HARD_FRAME_MAX = 2 * 1024 * 1024;
    /** 바닐라 채팅·명령 문자열 상한(ServerboundChatPacket·ServerboundChatCommandPacket). */
    public static final int VANILLA_CHAT_LENGTH = 256;

    // 단계별 기본 프레임 상한. 핸드셰이크·상태는 기존 값(ConnectionHandler), 로그인·구성은 바닐라가 받아 주는 가장 큰 패킷 크기.
    // 로그인: 서버 쪽 질의 응답 한 개(바닐라 ServerboundCustomQueryAnswerPacket 페이로드 1 MiB) + id·질의 번호.
    static final int LOGIN_FRAME_DEFAULT = 1 + 5 + 1 + 1_048_576;
    // 구성: custom_payload — id(1) + 채널 문자열(길이 3 + 32767자 × 최대 3바이트) + 페이로드(32767).
    static final int CONFIGURATION_FRAME_DEFAULT = 1 + 3 + 32767 * 3 + 32767;

    /** 한 접속 단계의 제한. 0 은 "제한 없음"(속도·시간) 이다. */
    public record StateLimits(int packetsPerSecond, int packetsBurst, int maxFrameBytes, int timeoutSeconds) {}

    private static final String[] STATE_KEYS = {"handshake", "status", "login", "configuration", "play"};

    private final StateLimits[] states = new StateLimits[5];
    private int readTimeoutSeconds = 30;
    private int vanillaRateLimit;        // server.properties rate-limit(0 = 끔)
    private int chatLength = VANILLA_CHAT_LENGTH;
    private int commandLength = VANILLA_CHAT_LENGTH;
    private int displayNameLength;       // 0 = 바닐라(추가 상한 없음)
    private int itemNameLength;
    private int loreLineLength;
    private int bookPageLength;
    private int bookAuthorLength;
    private boolean proxyProtocol;
    private List<Cidr> proxyTrusted = List.of();

    private ConnectionLimits() {
        states[0] = new StateLimits(200, 400, ConnectionHandler.HANDSHAKE_FRAME_MAX, 0);
        states[1] = new StateLimits(200, 400, ConnectionHandler.STATUS_FRAME_MAX, 0);
        states[2] = new StateLimits(200, 400, LOGIN_FRAME_DEFAULT, 30);   // 바닐라 slow_login(600틱)
        states[3] = new StateLimits(200, 400, CONFIGURATION_FRAME_DEFAULT, 0);
        states[4] = new StateLimits(200, 400, HARD_FRAME_MAX, 0);
    }

    /** 지금까지의 동작과 같은 기본값. */
    public static final ConnectionLimits DEFAULT = new ConnectionLimits();

    public StateLimits of(ConnectionState s) { return states[s.ordinal()]; }
    public int readTimeoutSeconds() { return readTimeoutSeconds; }
    /** server.properties rate-limit(초당 패킷 평균 상한, 0 = 끔). */
    public int vanillaRateLimit() { return vanillaRateLimit; }
    public int chatLength() { return chatLength; }
    public int commandLength() { return commandLength; }
    public int displayNameLength() { return displayNameLength; }
    public int itemNameLength() { return itemNameLength; }
    public int loreLineLength() { return loreLineLength; }
    public int bookPageLength() { return bookPageLength; }
    public int bookAuthorLength() { return bookAuthorLength; }
    public boolean proxyProtocol() { return proxyProtocol; }
    public List<Cidr> proxyTrusted() { return proxyTrusted; }

    /** 아이템 안의 글자 수 상한이 하나라도 켜져 있나(꺼져 있으면 검사 자체를 건너뛴다). */
    public boolean itemValidationEnabled() {
        return displayNameLength > 0 || itemNameLength > 0 || loreLineLength > 0 || bookPageLength > 0 || bookAuthorLength > 0;
    }

    /** 이 주소가 PROXY 헤더를 보내도 되는 앞단인가(목록이 비면 모두 허용). */
    public boolean proxyTrusts(InetAddress a) {
        if (proxyTrusted.isEmpty()) return true;
        if (a == null) return false;
        for (Cidr c : proxyTrusted) if (c.contains(a)) return true;
        return false;
    }

    private ConnectionLimits copy() {
        ConnectionLimits c = new ConnectionLimits();
        System.arraycopy(states, 0, c.states, 0, states.length);
        c.readTimeoutSeconds = readTimeoutSeconds;
        c.vanillaRateLimit = vanillaRateLimit;
        c.chatLength = chatLength;
        c.commandLength = commandLength;
        c.displayNameLength = displayNameLength;
        c.itemNameLength = itemNameLength;
        c.loreLineLength = loreLineLength;
        c.bookPageLength = bookPageLength;
        c.bookAuthorLength = bookAuthorLength;
        c.proxyProtocol = proxyProtocol;
        c.proxyTrusted = proxyTrusted;
        return c;
    }

    /** server.properties 의 rate-limit 을 넣은 사본. */
    public ConnectionLimits withVanillaRateLimit(int perSecond) {
        ConnectionLimits c = copy();
        c.vanillaRateLimit = Math.max(0, perSecond);
        return c;
    }

    /** furnace.properties 에 있을 수 있는 접속 방어 키(설정 점검·오타 경고용). */
    public static Set<String> knownKeys() {
        List<String> keys = new ArrayList<>(List.of("connection.read-timeout-seconds",
                "limits.chat-length", "limits.command-length",
                "limits.item.display-name", "limits.item.item-name", "limits.item.lore-line",
                "limits.item.book-page", "limits.item.book-author",
                "proxy-protocol", "proxy-protocol.trusted"));
        for (String s : STATE_KEYS) {
            keys.add("connection." + s + ".packets-per-second");
            keys.add("connection." + s + ".packets-burst");
            keys.add("connection." + s + ".max-frame-bytes");
            if (!s.equals("play")) keys.add("connection." + s + ".timeout-seconds");
        }
        return Set.copyOf(keys);
    }

    /** furnace.properties 를 읽는다(파일이 없으면 기본값). 잘못된 값은 IllegalArgumentException. */
    public static ConnectionLimits load(Path file, Consumer<String> warn) {
        if (!Files.exists(file)) return DEFAULT;
        Properties p = org.domi.furnace.server.config.FurnaceProps.read(file);
        return fromProperties(p, warn);
    }

    public static ConnectionLimits fromProperties(Properties p, Consumer<String> warn) {
        ConnectionLimits c = new ConnectionLimits();
        c.readTimeoutSeconds = intKey(p, "connection.read-timeout-seconds", 30, 1, 3600);
        for (int i = 0; i < STATE_KEYS.length; i++) {
            String s = STATE_KEYS[i];
            StateLimits d = c.states[i];
            int rate = intKey(p, "connection." + s + ".packets-per-second", d.packetsPerSecond, 0, 1_000_000);
            int burst = intKey(p, "connection." + s + ".packets-burst", d.packetsBurst, 1, 10_000_000);
            int frame = intKey(p, "connection." + s + ".max-frame-bytes", d.maxFrameBytes, 16, Integer.MAX_VALUE);
            if (frame > HARD_FRAME_MAX) {
                warn.accept("설정 'connection." + s + ".max-frame-bytes' 값 " + frame + " 는 프로토콜 최대 " + HARD_FRAME_MAX + " 를 넘어 조정합니다");
                frame = HARD_FRAME_MAX;
            }
            int timeout = s.equals("play") ? 0 : intKey(p, "connection." + s + ".timeout-seconds", d.timeoutSeconds, 0, 86_400);
            c.states[i] = new StateLimits(rate, burst, frame, timeout);
        }
        c.chatLength = intKey(p, "limits.chat-length", VANILLA_CHAT_LENGTH, 1, VANILLA_CHAT_LENGTH);
        c.commandLength = intKey(p, "limits.command-length", VANILLA_CHAT_LENGTH, 1, VANILLA_CHAT_LENGTH);
        c.displayNameLength = intKey(p, "limits.item.display-name", 0, 0, 1_000_000);
        c.itemNameLength = intKey(p, "limits.item.item-name", 0, 0, 1_000_000);
        c.loreLineLength = intKey(p, "limits.item.lore-line", 0, 0, 1_000_000);
        c.bookPageLength = intKey(p, "limits.item.book-page", 0, 0, 1_000_000);
        c.bookAuthorLength = intKey(p, "limits.item.book-author", 0, 0, 1_000_000);
        c.proxyProtocol = boolKey(p, "proxy-protocol", false);
        String trusted = p.getProperty("proxy-protocol.trusted", "").strip();
        if (!trusted.isEmpty()) {
            List<Cidr> list = new ArrayList<>();
            for (String part : trusted.split(",")) {
                String t = part.strip();
                if (!t.isEmpty()) list.add(Cidr.parse(t));
            }
            c.proxyTrusted = List.copyOf(list);
        }
        if (!c.proxyProtocol && p.containsKey("proxy-protocol.trusted")) {
            warn.accept("설정 'proxy-protocol.trusted' 는 'proxy-protocol=true' 일 때만 쓰입니다");
        }
        return c;
    }

    private static int intKey(Properties p, String key, int def, int min, int max) {
        String v = p.getProperty(key);
        if (v == null) return def;
        long n;
        try {
            n = Long.parseLong(v.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("설정 '" + key + "' 가 숫자가 아님: " + v, e);
        }
        if (n < min || n > max) {
            throw new IllegalArgumentException("설정 '" + key + "' 값 " + n + " 잘못됨(" + min + "~" + max + ")");
        }
        return (int) n;
    }

    private static boolean boolKey(Properties p, String key, boolean def) {
        String v = p.getProperty(key);
        if (v == null) return def;
        String t = v.strip().toLowerCase(Locale.ROOT);
        if (t.equals("true")) return true;
        if (t.equals("false")) return false;
        throw new IllegalArgumentException("설정 '" + key + "' 값 '" + v + "' 이(가) 잘못됐습니다: true 또는 false");
    }

    /** IP 대역(CIDR). 접두 길이를 생략하면 그 주소 하나. */
    public record Cidr(byte[] network, int prefix) {

        public static Cidr parse(String text) {
            String addr = text;
            int slash = text.indexOf('/');
            Integer prefix = null;
            if (slash >= 0) {
                addr = text.substring(0, slash);
                try {
                    prefix = Integer.parseInt(text.substring(slash + 1).strip());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("설정 'proxy-protocol.trusted' 의 접두 길이가 숫자가 아님: " + text, e);
                }
            }
            // 이름 조회를 피한다: 숫자 주소만 받는다(DNS 가 기동을 막거나 바뀌지 않게).
            if (!addr.strip().matches("[0-9a-fA-F:.]+")) {
                throw new IllegalArgumentException("설정 'proxy-protocol.trusted' 는 IP 주소나 CIDR 만 받습니다: " + text);
            }
            byte[] bytes;
            try {
                bytes = InetAddress.getByName(addr.strip()).getAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("설정 'proxy-protocol.trusted' 주소가 잘못됨: " + text, e);
            }
            int max = bytes.length * 8;
            int pre = prefix == null ? max : prefix;
            if (pre < 0 || pre > max) {
                throw new IllegalArgumentException("설정 'proxy-protocol.trusted' 접두 길이 범위 0~" + max + ": " + text);
            }
            return new Cidr(bytes, pre);
        }

        public boolean contains(InetAddress a) {
            byte[] b = a.getAddress();
            // IPv4 대역은 IPv4-mapped IPv6 로 들어온 주소와도 맞춘다.
            if (b.length == 16 && network.length == 4 && isV4Mapped(b)) b = java.util.Arrays.copyOfRange(b, 12, 16);
            if (b.length != network.length) return false;
            int full = prefix / 8;
            for (int i = 0; i < full; i++) if (b[i] != network[i]) return false;
            int rem = prefix % 8;
            if (rem == 0) return true;
            int mask = (0xFF << (8 - rem)) & 0xFF;
            return (b[full] & mask) == (network[full] & mask);
        }

        private static boolean isV4Mapped(byte[] b) {
            for (int i = 0; i < 10; i++) if (b[i] != 0) return false;
            return b[10] == (byte) 0xFF && b[11] == (byte) 0xFF;
        }
    }
}
