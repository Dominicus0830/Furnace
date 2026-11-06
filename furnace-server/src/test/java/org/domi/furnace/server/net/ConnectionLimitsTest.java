// 접속 방어 설정 파싱(기본값 = 지금까지 동작), CIDR, 바닐라 rate-limit 평균, 아이템 글자 수 상한
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.CodecException;
import org.domi.furnace.server.item.ComponentPatch;
import org.domi.furnace.server.net.protocol.ConnectionState;
import org.junit.jupiter.api.Test;

class ConnectionLimitsTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) p.setProperty(kv[i], kv[i + 1]);
        return p;
    }

    @Test
    void defaults_keepExistingBehaviour() {
        ConnectionLimits d = ConnectionLimits.DEFAULT;
        for (ConnectionState s : ConnectionState.values()) {
            assertEquals(200, d.of(s).packetsPerSecond(), s + " 초당");
            assertEquals(400, d.of(s).packetsBurst(), s + " 버스트");
        }
        assertEquals(ConnectionHandler.HANDSHAKE_FRAME_MAX, d.of(ConnectionState.HANDSHAKE).maxFrameBytes());
        assertEquals(ConnectionHandler.STATUS_FRAME_MAX, d.of(ConnectionState.STATUS).maxFrameBytes());
        assertEquals(ConnectionLimits.HARD_FRAME_MAX, d.of(ConnectionState.PLAY).maxFrameBytes());
        assertEquals(30, d.of(ConnectionState.LOGIN).timeoutSeconds(), "바닐라 slow_login 30초");
        assertEquals(0, d.of(ConnectionState.HANDSHAKE).timeoutSeconds());
        assertEquals(0, d.of(ConnectionState.CONFIGURATION).timeoutSeconds());
        assertEquals(30, d.readTimeoutSeconds(), "바닐라 ReadTimeoutHandler(30)");
        assertEquals(256, d.chatLength());
        assertEquals(256, d.commandLength());
        assertFalse(d.itemValidationEnabled(), "아이템 상한은 기본 꺼짐");
        assertFalse(d.proxyProtocol(), "PROXY 프로토콜은 기본 꺼짐");
        assertEquals(0, d.vanillaRateLimit());
        // 빈 파일 = 기본값과 같다
        ConnectionLimits parsed = ConnectionLimits.fromProperties(new Properties(), m -> fail(m));
        for (ConnectionState s : ConnectionState.values()) assertEquals(d.of(s), parsed.of(s));
    }

    @Test
    void defaultFrameLimits_coverLargestVanillaPackets() {
        // 로그인: 질의 응답 페이로드 1 MiB + id·질의 번호. 구성: custom_payload(채널 32767자 × 3B + 페이로드 32767).
        assertTrue(ConnectionLimits.DEFAULT.of(ConnectionState.LOGIN).maxFrameBytes() >= 1 + 5 + 1_048_576);
        assertTrue(ConnectionLimits.DEFAULT.of(ConnectionState.CONFIGURATION).maxFrameBytes() >= 1 + 3 + 32767 * 3 + 32767);
    }

    @Test
    void parsesPerStateKeys() {
        ConnectionLimits l = ConnectionLimits.fromProperties(props(
                "connection.login.packets-per-second", "5", "connection.login.packets-burst", "8",
                "connection.configuration.max-frame-bytes", "4096", "connection.configuration.timeout-seconds", "20",
                "connection.handshake.timeout-seconds", "7", "connection.play.packets-per-second", "0",
                "connection.read-timeout-seconds", "10"), m -> fail(m));
        assertEquals(new ConnectionLimits.StateLimits(5, 8, ConnectionLimits.LOGIN_FRAME_DEFAULT, 30), l.of(ConnectionState.LOGIN));
        assertEquals(4096, l.of(ConnectionState.CONFIGURATION).maxFrameBytes());
        assertEquals(20, l.of(ConnectionState.CONFIGURATION).timeoutSeconds());
        assertEquals(7, l.of(ConnectionState.HANDSHAKE).timeoutSeconds());
        assertEquals(0, l.of(ConnectionState.PLAY).packetsPerSecond(), "0 = 제한 없음");
        assertEquals(10, l.readTimeoutSeconds());
    }

    @Test
    void frameBytesAboveProtocolMaxAreClamped() {
        List<String> warns = new ArrayList<>();
        ConnectionLimits l = ConnectionLimits.fromProperties(props("connection.play.max-frame-bytes", "999999999"), warns::add);
        assertEquals(ConnectionLimits.HARD_FRAME_MAX, l.of(ConnectionState.PLAY).maxFrameBytes());
        assertEquals(1, warns.size());
    }

    @Test
    void invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("connection.login.packets-burst", "0"), m -> {}));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("connection.status.packets-per-second", "abc"), m -> {}));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("connection.login.timeout-seconds", "-1"), m -> {}));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("limits.chat-length", "257"), m -> {}),
                "채팅·명령은 바닐라 256 을 넘길 수 없다(조일 수만 있다)");
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("limits.command-length", "0"), m -> {}));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("proxy-protocol", "yes"), m -> {}));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.fromProperties(props("proxy-protocol", "true", "proxy-protocol.trusted", "example.com"), m -> {}),
                "이름 조회는 하지 않는다");
    }

    @Test
    void knownKeysListEveryParsedKey() {
        var keys = ConnectionLimits.knownKeys();
        assertTrue(keys.contains("connection.login.packets-per-second"));
        assertTrue(keys.contains("connection.configuration.timeout-seconds"));
        assertFalse(keys.contains("connection.play.timeout-seconds"), "플레이 단계에는 시간 제한 키가 없다");
        assertTrue(keys.contains("proxy-protocol.trusted"));
        assertTrue(keys.contains("limits.item.lore-line"));
    }

    @Test
    void cidr_matchesV4AndMappedAndV6() throws Exception {
        var c = ConnectionLimits.Cidr.parse("10.1.0.0/16");
        assertTrue(c.contains(InetAddress.getByName("10.1.200.3")));
        assertFalse(c.contains(InetAddress.getByName("10.2.0.1")));
        assertTrue(c.contains(InetAddress.getByName("::ffff:10.1.0.9")), "IPv4-mapped IPv6 로 와도 같은 대역");
        assertTrue(ConnectionLimits.Cidr.parse("127.0.0.1").contains(InetAddress.getByName("127.0.0.1")));
        assertFalse(ConnectionLimits.Cidr.parse("127.0.0.1").contains(InetAddress.getByName("127.0.0.2")));
        var v6 = ConnectionLimits.Cidr.parse("2001:db8::/32");
        assertTrue(v6.contains(InetAddress.getByName("2001:db8:ffff::1")));
        assertFalse(v6.contains(InetAddress.getByName("2001:db9::1")));
        assertFalse(v6.contains(InetAddress.getByName("10.1.0.1")));
        assertTrue(ConnectionLimits.Cidr.parse("0.0.0.0/0").contains(InetAddress.getByName("203.0.113.5")));
        assertThrows(IllegalArgumentException.class, () -> ConnectionLimits.Cidr.parse("10.0.0.0/33"));
    }

    @Test
    void proxyTrust_emptyListTrustsAll_listRestricts() throws Exception {
        assertTrue(ConnectionLimits.DEFAULT.proxyTrusts(InetAddress.getByName("203.0.113.5")));
        ConnectionLimits l = ConnectionLimits.fromProperties(props("proxy-protocol", "true", "proxy-protocol.trusted", "10.0.0.0/8, 192.168.1.5"), m -> {});
        assertTrue(l.proxyProtocol());
        assertTrue(l.proxyTrusts(InetAddress.getByName("10.9.9.9")));
        assertTrue(l.proxyTrusts(InetAddress.getByName("192.168.1.5")));
        assertFalse(l.proxyTrusts(InetAddress.getByName("203.0.113.5")));
        assertFalse(l.proxyTrusts(null));
    }

    @Test
    void receiveRateWatch_exactNumbers() {
        ReceiveRateWatch w = new ReceiveRateWatch(10);
        for (int i = 0; i < 20; i++) w.onPacket();
        w.tickSecond();
        assertEquals(5.0f, w.average(), 1e-6, "20 + 0.75 × (0 - 20) = 5");
        for (int i = 0; i < 4; i++) w.onPacket();
        w.tickSecond();
        assertEquals(4 + 0.75f * (5 - 4), w.average(), 1e-6);
    }

    @Test
    void receiveRateWatch_kicksOnlyWhenAverageExceedsLimit() {
        ReceiveRateWatch w = new ReceiveRateWatch(10);
        for (int sec = 0; sec < 5; sec++) {
            for (int i = 0; i < 10; i++) w.onPacket();
            assertFalse(w.tickSecond(), "초당 10개 = 상한은 초과가 아니다");
        }
        for (int i = 0; i < 100; i++) w.onPacket();
        assertTrue(w.tickSecond(), "한 번에 크게 넘치면 평균이 상한을 넘는다");
    }

    // ------------------------------------------------------------------ 아이템 글자 수 상한

    private static ItemStack stack(Map<String, Object> comps) {
        return new ItemStack(1, 1, ComponentPatch.ofNormalized(comps));
    }

    private static ConnectionLimits itemLimits(String... kv) {
        return ConnectionLimits.fromProperties(props(kv), m -> {});
    }

    @Test
    void itemValidation_offByDefault() {
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("minecraft:custom_name", "\"" + "x".repeat(100_000) + "\"");
        ItemValidation.check(stack(c), ConnectionLimits.DEFAULT);   // 던지지 않는다
    }

    @Test
    void itemValidation_namesAndLore() {
        ConnectionLimits l = itemLimits("limits.item.display-name", "10", "limits.item.item-name", "20", "limits.item.lore-line", "5");
        Map<String, Object> ok = new LinkedHashMap<>();
        ok.put("minecraft:custom_name", "\"0123456\"");   // 10자
        ok.put("minecraft:item_name", "x".repeat(20));
        ok.put("minecraft:lore", List.of("abcde", "ab"));
        ItemValidation.check(stack(ok), l);

        for (var bad : List.of(
                Map.<String, Object>of("minecraft:custom_name", "x".repeat(11)),
                Map.<String, Object>of("minecraft:item_name", "x".repeat(21)),
                Map.<String, Object>of("minecraft:lore", List.of("ok", "toolong")))) {
            CodecException e = assertThrows(CodecException.class, () -> ItemValidation.check(stack(bad), l));
            assertTrue(e.getMessage().startsWith("item-validation"), e.getMessage());
        }
    }

    @Test
    void itemValidation_books() {
        ConnectionLimits l = itemLimits("limits.item.book-page", "8", "limits.item.book-author", "4");
        Map<String, Object> written = new LinkedHashMap<>();
        written.put("title", Map.of("raw", "t"));
        written.put("author", "abcd");
        written.put("pages", List.of(Map.of("raw", "12345678")));
        ItemValidation.check(stack(Map.of("minecraft:written_book_content", written)), l);

        Map<String, Object> longPage = new LinkedHashMap<>(written);
        longPage.put("pages", List.of(Map.of("raw", "ok"), Map.of("raw", "123456789")));
        assertThrows(CodecException.class, () -> ItemValidation.check(stack(Map.of("minecraft:written_book_content", longPage)), l));

        Map<String, Object> longAuthor = new LinkedHashMap<>(written);
        longAuthor.put("author", "abcde");
        assertThrows(CodecException.class, () -> ItemValidation.check(stack(Map.of("minecraft:written_book_content", longAuthor)), l));

        Map<String, Object> writable = Map.of("pages", List.of(Map.of("raw", "123456789")));
        assertThrows(CodecException.class, () -> ItemValidation.check(stack(Map.of("minecraft:writable_book_content", writable)), l));
    }

    @Test
    void itemValidation_looksInsideContainers() {
        ConnectionLimits l = itemLimits("limits.item.display-name", "5");
        Map<String, Object> inner = Map.of("id", "minecraft:stick", "count", 1,
                "components", Map.of("minecraft:custom_name", "x".repeat(6)));
        Map<String, Object> outer = Map.of("minecraft:container", List.of(Map.of("slot", 0, "item", inner)));
        assertThrows(CodecException.class, () -> ItemValidation.check(stack(outer), l), "상자 안에 숨긴 긴 이름도 막는다");
    }

    @Test
    void itemValidation_rejectsAbsurdNesting() {
        ConnectionLimits l = itemLimits("limits.item.display-name", "5");
        Map<String, Object> v = Map.of("minecraft:custom_name", "x");
        for (int i = 0; i < 40; i++) v = Map.of("minecraft:container", List.of(Map.of("item", Map.of("components", v))));
        Map<String, Object> deep = v;
        assertThrows(CodecException.class, () -> ItemValidation.check(stack(deep), l));
    }

    /** 깨진 텍스트 컴포넌트(잘린 JSON, 잘못된 UUID 의 show_entity hover)는 CodecException 으로 거부되거나 안전하게 받아들여질 뿐, 다른 예외로 번지지 않는다. */
    @Test
    void malformedTextComponents_neverEscapeAsOtherExceptions() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        for (String bad : List.of("{\"text\":", "{\"text\":\"a\",\"extra\":[[[[1]]]]}",
                "{\"text\":\"a\",\"hoverEvent\":{\"action\":\"show_entity\",\"contents\":{\"type\":\"minecraft:pig\",\"id\":\"not-a-uuid\"}}}",
                "{\"translate\":5}")) {
            try {
                ComponentPatch.of(Map.of("minecraft:custom_name", bad));
            } catch (CodecException ok) {
                // 거부 — 디코드 실패와 같이 연결이 끊기는 경로
            }
        }
        assertThrows(CodecException.class, () -> ComponentPatch.of(Map.of("minecraft:custom_name", "{\"text\":")), "잘린 JSON 은 거부");
    }
}
