// S→C 체력/허기(0x5D): float 체력, VarInt 허기, float 포화도.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetHealth(float health, int food, float saturation) implements Packet {

    public static final int ID = 0x5D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeFloat(health);
        Types.writeVarInt(buf, food);
        buf.writeFloat(saturation);
    }
}
