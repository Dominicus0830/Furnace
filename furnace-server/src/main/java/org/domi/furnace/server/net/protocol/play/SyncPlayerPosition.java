// S→C 플레이어 위치 동기화. 클라는 같은 teleportId로 Confirm Teleportation을 보내야 한다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SyncPlayerPosition(double x, double y, double z, int teleportId) implements Packet {

    public static final int ID = 0x40;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(0f);          // Yaw
        buf.writeFloat(0f);          // Pitch
        buf.writeByte(0);            // Flags (모두 절대값)
        Types.writeVarInt(buf, teleportId);
    }
}
