// resource_pack_push 직렬화와 응답 상태 분류 — 바이트는 바닐라 ClientboundResourcePackPushPacket 코덱을 손으로 따라간 값.
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.ResourcePackResponses;
import org.junit.jupiter.api.Test;

class ResourcePackPushTest {

    private static String hex(ResourcePackPush p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        String h = ByteBufUtil.hexDump(b);
        b.release();
        return h;
    }

    @Test
    void ids() {
        UUID id = new UUID(1L, 2L);
        assertEquals(0x09, new ResourcePackPush(id, "u", "", false, null, false).id());
        assertEquals(0x46, new ResourcePackPush(id, "u", "", false, null, true).id());
    }

    @Test
    void withoutPrompt() {
        // UUID(1,2) = 8바이트 1, 8바이트 2; URL "u"; 해시 ""; 필수 true; 안내 없음
        assertEquals("0000000000000001" + "0000000000000002" + "0175" + "00" + "01" + "00",
                hex(new ResourcePackPush(new UUID(1L, 2L), "u", "", true, null, false)));
    }

    @Test
    void withPrompt_startsWithPresentFlagThenNbt() {
        String h = hex(new ResourcePackPush(new UUID(1L, 2L), "u", "ab", false, Text.text("hi"), true));
        String head = "0000000000000001" + "0000000000000002" + "0175" + "026162" + "00" + "01";
        assertTrue(h.startsWith(head));
        assertEquals(head + ByteBufUtil.hexDump(Text.text("hi").networkNbt()), h);
    }

    @Test
    void hashTooLongRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResourcePackPush(new UUID(0, 0), "u", "x".repeat(41), false, null, false));
    }

    @Test
    void responseStatus() {
        assertTrue(ResourcePackResponses.isTerminal(0));
        assertTrue(ResourcePackResponses.isTerminal(1));
        assertTrue(ResourcePackResponses.isTerminal(2));
        assertFalse(ResourcePackResponses.isTerminal(3));   // ACCEPTED
        assertFalse(ResourcePackResponses.isTerminal(4));   // DOWNLOADED
        assertTrue(ResourcePackResponses.isTerminal(7));
        assertTrue(ResourcePackResponses.kicksRequired(1, true));
        assertFalse(ResourcePackResponses.kicksRequired(1, false));
        assertFalse(ResourcePackResponses.kicksRequired(2, true));
    }
}
