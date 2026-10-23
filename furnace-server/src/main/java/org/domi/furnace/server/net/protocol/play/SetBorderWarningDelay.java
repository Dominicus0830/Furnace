// S→C 월드 보더 경고 시간(0x50): VarInt 초 (ClientboundSetBorderWarningDelayPacket).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.WorldBorder;

public record SetBorderWarningDelay(int warningTimeSeconds) implements Packet {

    public static final int ID = 0x50;

    public static SetBorderWarningDelay of(WorldBorder border) {
        return new SetBorderWarningDelay(border.getWarningTime());
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, warningTimeSeconds);
    }
}
