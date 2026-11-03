// BungeeCord 레거시 포워딩 주소 칸 풀기: Spigot 규약(칸 3·4개, IP 칸 모양, 대시 없는 UUID, 속성 이름 검사)
package org.domi.furnace.server.net.auth;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BungeeForwardingTest {

    private static final UUID NOTCH = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5");

    @Test
    void parsesThreeFields() throws Exception {
        var f = BungeeForwarding.parse("mc.example.com\0" + "203.0.113.5\0" + "069a79f444e94726a5befca90e38aaf5");
        assertNotNull(f);
        assertEquals("mc.example.com", f.hostName());
        assertEquals(InetAddress.getByName("203.0.113.5"), f.address());
        assertEquals(NOTCH, f.uuid());
        assertTrue(f.properties().isEmpty());
    }

    @Test
    void parsesPropertiesAndDropsBadNames() {
        String json = "[{\"name\":\"textures\",\"value\":\"dGV4\",\"signature\":\"c2ln\"},"
                + "{\"name\":\"no-dash allowed\",\"value\":\"x\"},{\"name\":\"plain\",\"value\":\"v\"}]";
        var f = BungeeForwarding.parse("host\0" + "203.0.113.5\0" + "069a79f4-44e9-4726-a5be-fca90e38aaf5\0" + json);
        assertNotNull(f, "대시가 있는 UUID 도 받는다(fromStringLenient)");
        assertEquals(NOTCH, f.uuid());
        assertEquals(2, f.properties().size(), "PROP_PATTERN(\\w{0,16})에 맞지 않는 이름은 버린다");
        assertEquals("textures", f.properties().get(0).name());
        assertEquals("c2ln", f.properties().get(0).signature());
        assertNull(f.properties().get(1).signature());
    }

    @Test
    void acceptsIpv6Literal() throws Exception {
        var f = BungeeForwarding.parse("h\0" + "2001:db8::1\0" + "069a79f444e94726a5befca90e38aaf5");
        assertNotNull(f);
        assertEquals(InetAddress.getByName("2001:db8::1"), f.address());
    }

    @Test
    void rejectsMalformed() {
        String uuid = "069a79f444e94726a5befca90e38aaf5";
        assertNull(BungeeForwarding.parse("localhost"), "포워딩 없는 평범한 주소");
        assertNull(BungeeForwarding.parse("h\0" + "1.2.3.4"), "칸 2개");
        assertNull(BungeeForwarding.parse("h\0" + "1.2.3.4\0" + uuid + "\0[]\0x"), "칸 5개");
        assertNull(BungeeForwarding.parse("h\0" + "evil.host\0" + uuid), "IP 칸에 허용되지 않는 문자");
        assertNull(BungeeForwarding.parse("h\0" + "abc\0" + uuid), "IP 리터럴이 아니면 DNS 를 타지 않고 거절");
        assertNull(BungeeForwarding.parse("h\0" + "999.1.1.1\0" + uuid), "옥텟 범위 밖");
        assertNull(BungeeForwarding.parse("h\0" + "1.2.3.4\0" + "notauuid"), "UUID 형식");
        assertNull(BungeeForwarding.parse("h\0" + "1.2.3.4\0" + uuid + "\0{not json"), "속성 JSON 형식");
    }

    @Test
    void looksForwardedMatchesSpigotCheck() {
        assertTrue(BungeeForwarding.looksForwarded("h\0" + "1.2.3.4\0" + "x"));
        assertFalse(BungeeForwarding.looksForwarded("localhost"));
        assertFalse(BungeeForwarding.looksForwarded("h\0" + "evil.host\0" + "x"));
    }
}
