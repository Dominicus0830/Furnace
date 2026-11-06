// tag_query(0x6E)·custom_payload(play 0x19 / 구성 0x01) 와이어 형식 — 바닐라 StreamCodec 을 손으로 따라간 바이트.
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.Packet;
import org.junit.jupiter.api.Test;

class TagQueryAndPayloadTest {

    private static String hex(Packet p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        String h = ByteBufUtil.hexDump(b);
        b.release();
        return h;
    }

    @Test
    void tagQuery_nullAndEmptyCompound() {
        assertEquals(0x6E, new TagQuery(1, null).id());
        assertEquals("0500", hex(new TagQuery(5, null)));            // 처리 번호 5, TAG_END
        assertEquals("05" + "0a" + "00", hex(new TagQuery(5, new NbtCompound())));   // 빈 무명 루트 compound
    }

    @Test
    void customPayload_channelThenRawBytes() {
        // "a:b" = 03 61 3a 62, 본문 01 02
        assertEquals("03613a62" + "0102", hex(new CustomPayloadPacket("a:b", new byte[] {1, 2}, true)));
        assertEquals(0x19, new CustomPayloadPacket("a:b", new byte[0], true).id());
        assertEquals(0x01, new CustomPayloadPacket("a:b", new byte[0], false).id());
        assertThrows(IllegalArgumentException.class,
                () -> hex(new CustomPayloadPacket("a:b", new byte[CustomPayloadPacket.MAX_CLIENTBOUND + 1], true)));
    }
}
