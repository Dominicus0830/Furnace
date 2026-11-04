// minecraft:register 본문(NUL 로 이은 채널 이름) 읽기·쓰기와 연결당 상한.
package org.domi.furnace.server.net;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PluginChannelsTest {

    private static byte[] b(String s) { return s.getBytes(StandardCharsets.UTF_8); }

    @Test
    void parseSplitsOnNulSkipsEmptyAndInvalidNames() {
        assertEquals(List.of("a:b", "minecraft:plain", "c:d/e"), PluginChannels.parse(b("a:b\0\0plain\0Bad:Name\0c:d/e\0")));
        assertEquals(List.of(), PluginChannels.parse(new byte[0]));
    }

    @Test
    void encodeJoinsWithNulWithoutTrailing() {
        assertArrayEquals(b("a:b\0c:d"), PluginChannels.encode(List.of("a:b", "c:d")));
        assertEquals(List.of("a:b", "c:d"), PluginChannels.parse(PluginChannels.encode(List.of("a:b", "c:d"))));
    }

    @Test
    void registerAndUnregisterTrackChannels() {
        PluginChannels c = new PluginChannels();
        assertEquals(2, c.register(b("a:b\0c:d")));
        assertEquals(0, c.register(b("a:b")), "이미 있는 채널은 다시 세지 않는다");
        assertTrue(c.listening("c:d"));
        assertEquals(1, c.unregister(b("c:d\0x:y")));
        assertEquals(Set.of("a:b"), c.snapshot());
    }

    @Test
    void capsChannelsPerConnection() {
        PluginChannels c = new PluginChannels();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PluginChannels.MAX_CHANNELS + 10; i++) sb.append("p:c").append(i).append('\0');
        assertEquals(PluginChannels.MAX_CHANNELS, c.register(b(sb.toString())));
        assertEquals(PluginChannels.MAX_CHANNELS, c.snapshot().size());
        c.unregister(b("p:c0"));
        assertEquals(1, c.register(b("new:one")), "해제하면 자리가 난다");
    }
}
