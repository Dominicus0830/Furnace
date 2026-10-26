// S→C 탈것 위치 되돌리기(move_vehicle 0x31): double x·y·z, float yRot·xRot(바닐라 ClientboundMoveVehiclePacket). 받은 탈것 이동을 거부할 때 보낸다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record MoveVehicle(double x, double y, double z, float yRot, float xRot) implements Packet {

    public static final int ID = 0x31;

    public static MoveVehicle of(org.domi.furnace.server.entity.Entity e) { return new MoveVehicle(e.x(), e.y(), e.z(), e.yRot(), e.xRot()); }

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(yRot);
        buf.writeFloat(xRot);
    }
}
