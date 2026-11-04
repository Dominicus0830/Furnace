// S→C 플레이어 위치 동기화. 클라는 같은 teleportId로 Confirm Teleportation을 보내야 한다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SyncPlayerPosition(double x, double y, double z, float yaw, float pitch, int flags, int teleportId) implements Packet {

    /** RelativeMovement 다섯 축(X·Y·Z·Y_ROT·X_ROT) 모두 상대값. */
    public static final int RELATIVE_ALL = 0x1F;

    /** 모두 절대값. */
    public SyncPlayerPosition(double x, double y, double z, float yaw, float pitch, int teleportId) {
        this(x, y, z, yaw, pitch, 0, teleportId);
    }

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
        buf.writeFloat(yaw);
        buf.writeFloat(pitch);
        buf.writeByte(flags);        // RelativeMovement 비트(0 = 모두 절대값)
        Types.writeVarInt(buf, teleportId);
    }
}
