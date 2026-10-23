// S→C 월드 보더 중심 변경(0x4D): double x, double z (바닐라 ClientboundSetBorderCenterPacket).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;

public record SetBorderCenter(double centerX, double centerZ) implements Packet {

    public static final int ID = 0x4D;

    public static SetBorderCenter of(WorldBorder border) {
        synchronized (border) {
            return new SetBorderCenter(border.getCenterX(), border.getCenterZ());
        }
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(centerX);
        buf.writeDouble(centerZ);
    }
}
