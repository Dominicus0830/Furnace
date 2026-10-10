// S→C 월드 보더 경고 거리(0x51): VarInt 블록 (ClientboundSetBorderWarningDistancePacket).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;

public record SetBorderWarningDistance(int warningBlocks) implements Packet {

    public static final int ID = 0x51;

    public static SetBorderWarningDistance of(WorldBorder border) {
        return new SetBorderWarningDistance(border.getWarningBlocks());
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, warningBlocks);
    }
}
