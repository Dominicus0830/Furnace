package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.domi.furnace.api.nbt.NbtCompound;
import org.domi.furnace.api.nbt.NbtList;
import org.junit.jupiter.api.Test;

/** 패킷 NBT(1.20.2+ 이름 없는 뿌리) 읽기·쓰기 — 청크 패킷 shim(T-182)의 하이트맵·블록 엔티티 태그 길. PR #399 검토 S4(M5·M13). */
class NbtWireTest {

    @Test
    void emptyListIsWrittenWithEndElementTypeLikeVanilla() {
        ByteBuf b = Unpooled.buffer();
        NbtWire.write(b, new NbtCompound().put("l", new NbtList()));
        // 뿌리 10, [9 "l" 원소종류 0 길이 0], 끝 0 — 바닐라 ListTag 는 빈 목록의 원소 종류를 0(END)으로 쓴다
        assertEquals("0a" + "09" + "0001" + "6c" + "00" + "00000000" + "00", ByteBufUtil.hexDump(b));
        assertEquals(new NbtCompound().put("l", new NbtList()), NbtWire.read(b));
    }

    @Test
    void nestingDeeperThanVanillaIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> NbtWire.read(nested(600)));
        ByteBuf ok = nested(500);
        NbtCompound c = NbtWire.read(ok);
        assertTrue(c.contains("c"));
        assertEquals(0, ok.readableBytes());
        assertNull(NbtWire.read(Unpooled.wrappedBuffer(new byte[] {0})), "태그 0 = 없음");
    }

    /** 뿌리 안에 compound 를 n 겹 넣은 바이트. */
    private static ByteBuf nested(int n) {
        ByteBuf b = Unpooled.buffer();
        b.writeByte(10);
        for (int i = 0; i < n; i++) {
            b.writeByte(10);
            b.writeShort(1);
            b.writeByte('c');
        }
        for (int i = 0; i <= n; i++) b.writeByte(0);
        return b;
    }
}
