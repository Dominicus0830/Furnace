package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarLong;
import org.junit.jupiter.api.Test;

/** 바닐라 정적 도우미(ByteBuf 에 바로 읽고 쓰기)가 인스턴스 도우미와 같은 바이트를 낸다. */
class NmsByteBufStaticsTest {

    @Test
    void uuidBlockPosAndByteArrayRoundTripThroughStatics() {
        ByteBuf buf = Unpooled.buffer();
        UUID id = new UUID(0x0123456789ABCDEFL, 0xFEDCBA9876543210L);
        FriendlyByteBuf.writeUUID(buf, id);
        FriendlyByteBuf.writeBlockPos(buf, new BlockPos(-5, 70, 12345));
        FriendlyByteBuf.writeByteArray(buf, new byte[] {1, 2, 3});
        assertEquals(id, FriendlyByteBuf.readUUID(buf));
        assertEquals(new BlockPos(-5, 70, 12345), FriendlyByteBuf.readBlockPos(buf));
        assertArrayEquals(new byte[] {1, 2, 3}, FriendlyByteBuf.readByteArray(buf, 8));
        assertEquals(0, buf.readableBytes());
        FriendlyByteBuf inst = new FriendlyByteBuf(Unpooled.buffer());
        inst.writeUUID(id).writeBlockPos(new BlockPos(1, 2, 3));
        assertEquals(id, FriendlyByteBuf.readUUID(inst));
    }

    @Test
    void varLongMatchesVanillaBytes() {
        ByteBuf buf = Unpooled.buffer();
        long[] values = {0L, 1L, 127L, 128L, 300L, -1L, Long.MAX_VALUE, Long.MIN_VALUE};
        for (long v : values) {
            VarLong.write(buf, v);
            assertEquals(VarLong.getByteSize(v), buf.readableBytes());
            assertEquals(v, VarLong.read(buf));
        }
        assertEquals(10, VarLong.getByteSize(-1L));
        ByteBuf bad = Unpooled.buffer();
        for (int i = 0; i < 11; i++) bad.writeByte(0x80);
        assertThrows(RuntimeException.class, () -> VarLong.read(bad));
    }
}
