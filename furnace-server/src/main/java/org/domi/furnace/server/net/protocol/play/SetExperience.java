// S→C 경험치(0x5C): float 진행도, VarInt 레벨, VarInt 총 경험치.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetExperience(float progress, int level, int total) implements Packet {

    public static final int ID = 0x5C;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeFloat(progress);
        Types.writeVarInt(buf, level);
        Types.writeVarInt(buf, total);
    }
}
