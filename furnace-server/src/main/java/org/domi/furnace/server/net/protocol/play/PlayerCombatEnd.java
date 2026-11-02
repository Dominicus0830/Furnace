// S→C 전투 끝(0x3A): VarInt 전투 지속 틱. 바닐라 CombatTracker.recheckStatus.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerCombatEnd(int duration) implements Packet {

    public static final int ID = 0x3A;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { Types.writeVarInt(buf, duration); }
}
