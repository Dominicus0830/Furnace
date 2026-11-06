// 월드 보더 패킷(play, S→C)의 id(데이터 생성기 packets.json)와 바이트 배치, 보더 상태로부터의 생성.
package org.domi.furnace.server.net.protocol.play;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;
import org.junit.jupiter.api.Test;

class BorderPacketsTest {

    private static ByteBuf write(Packet p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        return b;
    }

    private static long readVarLong(ByteBuf b) {
        long v = 0;
        int shift = 0;
        byte cur;
        do {
            cur = b.readByte();
            v |= (long) (cur & 0x7F) << shift;
            shift += 7;
        } while ((cur & 0x80) != 0);
        return v;
    }

    @Test
    void ids() {
        assertEquals(37, InitializeBorder.ID);
        assertEquals(77, SetBorderCenter.ID);
        assertEquals(78, SetBorderLerpSize.ID);
        assertEquals(79, SetBorderSize.ID);
        assertEquals(80, SetBorderWarningDelay.ID);
        assertEquals(81, SetBorderWarningDistance.ID);
    }

    @Test
    void initializeBorderCarriesLiveState() {
        long[] now = {0};
        WorldBorder border = new WorldBorder(() -> now[0]);
        border.setCenter(12.5, -3.0);
        border.setWarningBlocks(9);
        border.setWarningTime(20);
        border.lerpSizeBetween(100.0, 400.0, 60_000L);
        now[0] = 15_000L;
        InitializeBorder p = InitializeBorder.of(border);
        assertEquals(0x25, p.id());
        ByteBuf b = write(p);
        assertEquals(12.5, b.readDouble());
        assertEquals(-3.0, b.readDouble());
        assertEquals(175.0, b.readDouble(), 1e-9);        // 현재 크기
        assertEquals(400.0, b.readDouble());              // 보간 목표
        assertEquals(45_000L, readVarLong(b));            // 남은 보간 시간
        assertEquals(29999984, Types.readVarInt(b));
        assertEquals(9, Types.readVarInt(b));             // 경고 거리(블록)
        assertEquals(20, Types.readVarInt(b));            // 경고 시간(초)
        assertEquals(0, b.readableBytes());
    }

    @Test
    void initializeBorderOfDefaultBorderEqualsVanillaDefault() {
        assertEquals(InitializeBorder.vanillaDefault(), InitializeBorder.of(new WorldBorder()));
    }

    @Test
    void setBorderCenter() {
        WorldBorder border = new WorldBorder();
        border.setCenter(1.5, 2.5);
        ByteBuf b = write(SetBorderCenter.of(border));
        assertEquals(1.5, b.readDouble());
        assertEquals(2.5, b.readDouble());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void setBorderLerpSize() {
        long[] now = {0};
        WorldBorder border = new WorldBorder(() -> now[0]);
        border.lerpSizeBetween(100.0, 50.0, 10_000L);
        ByteBuf b = write(SetBorderLerpSize.of(border));
        assertEquals(100.0, b.readDouble());
        assertEquals(50.0, b.readDouble());
        assertEquals(10_000L, readVarLong(b));
        assertEquals(0, b.readableBytes());
    }

    @Test
    void setBorderSizeSendsLerpTarget() {
        WorldBorder border = new WorldBorder();
        border.setSize(321.0);
        ByteBuf b = write(SetBorderSize.of(border));
        assertEquals(321.0, b.readDouble());
        assertEquals(0, b.readableBytes());
    }

    @Test
    void warningPackets() {
        WorldBorder border = new WorldBorder();
        border.setWarningTime(300);
        border.setWarningBlocks(1);
        ByteBuf d = write(SetBorderWarningDelay.of(border));
        assertEquals(300, Types.readVarInt(d));
        assertEquals(0, d.readableBytes());
        ByteBuf n = write(SetBorderWarningDistance.of(border));
        assertEquals(1, Types.readVarInt(n));
        assertEquals(0, n.readableBytes());
    }
}
