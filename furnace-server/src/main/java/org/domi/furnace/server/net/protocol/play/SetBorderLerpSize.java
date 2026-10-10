// S→C 월드 보더 크기 보간 시작(0x4E): double 현재 크기, double 목표 크기, VarLong 남은 시간(ms) (ClientboundSetBorderLerpSizePacket).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;

public record SetBorderLerpSize(double oldSize, double newSize, long lerpMillis) implements Packet {

    public static final int ID = 0x4E;

    public static SetBorderLerpSize of(WorldBorder border) {
        synchronized (border) {
            return new SetBorderLerpSize(border.getSize(), border.getLerpTarget(), border.getLerpRemainingTime());
        }
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(oldSize);
        buf.writeDouble(newSize);
        Types.writeVarLong(buf, lerpMillis);
    }
}
