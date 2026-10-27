// S→C 점수 지우기(0x44): String 주인, Optional<String> 목표(없으면 그 주인의 모든 점수). 바닐라 ClientboundResetScorePacket.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ResetScore(String owner, String objectiveName) implements Packet {

    public static final int ID = 0x44;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, owner);
        buf.writeBoolean(objectiveName != null);
        if (objectiveName != null) Types.writeString(buf, objectiveName);
    }
}
