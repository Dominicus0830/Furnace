// S→C 표시 슬롯(0x57): VarInt 슬롯 번호(DisplaySlot id), String 목표 이름(비우면 "" — 슬롯 비움). 바닐라 ClientboundSetDisplayObjectivePacket.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.scores.DisplaySlot;
import org.domi.furnace.server.scores.Objective;

public record SetDisplayObjective(DisplaySlot slot, String objectiveName) implements Packet {

    public static final int ID = 0x57;

    public static SetDisplayObjective of(DisplaySlot slot, Objective o) { return new SetDisplayObjective(slot, o == null ? "" : o.name()); }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, slot.id());
        Types.writeString(buf, objectiveName);
    }
}
