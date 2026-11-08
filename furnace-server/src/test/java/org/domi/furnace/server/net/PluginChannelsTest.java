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
    void parseSplitsOnNulAndSkipsEmpty() {
        assertEquals(List.of("a:b", "plain", "Bad:Name", "c:d/e"), PluginChannels.parse(b("a:b\0\0plain\0Bad:Name\0c:d/e\0")));
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
    void register129thChannelIsInvalidLikePaper() {
        PluginChannels c = new PluginChannels();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PluginChannels.MAX_CHANNELS; i++) sb.append("p:c").append(i).append('\0');
        assertEquals(PluginChannels.MAX_CHANNELS, c.register(b(sb.toString())));
        assertThrows(PluginChannels.InvalidChannelException.class, () -> c.register(b("new:one")));
        assertThrows(PluginChannels.InvalidChannelException.class, () -> c.register(b("p:c0")), "이미 있는 이름이어도 Paper 는 개수부터 검사한다");
        assertEquals(PluginChannels.MAX_CHANNELS, c.snapshot().size());
        c.unregister(b("p:c0"));
        assertEquals(1, c.register(b("new:one")), "해제하면 자리가 난다");
    }

    @Test
    void invalidNamesThrowLikePaper() {
        PluginChannels c = new PluginChannels();
        assertThrows(PluginChannels.InvalidChannelException.class, () -> c.register(b("nocolon")));
        assertThrows(PluginChannels.InvalidChannelException.class, () -> c.register(b("Up:per")));
        assertThrows(PluginChannels.InvalidChannelException.class, () -> c.unregister(b("nocolon")));
        assertThrows(PluginChannels.InvalidChannelException.class, () -> c.register(b("a:" + "x".repeat(PluginChannels.MAX_CHANNEL_SIZE))));
        assertEquals(1, c.register(b("a:" + "x".repeat(PluginChannels.MAX_CHANNEL_SIZE - 2))), "상한 길이까지는 받는다");
        assertEquals(1, c.register(b("a b:c d")), "Paper 는 ':' 와 소문자만 본다");
    }

    @Test
    void bungeeCordNameIsCorrected() {
        PluginChannels c = new PluginChannels();
        assertEquals(1, c.register(b("BungeeCord")));
        assertEquals(Set.of("bungeecord:main"), c.snapshot());
        assertEquals(1, c.unregister(b("BungeeCord")));
    }
}
