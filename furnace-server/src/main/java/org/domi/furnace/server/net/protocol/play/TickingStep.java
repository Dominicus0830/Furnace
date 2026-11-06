// S→C ticking_step(0x72, 바닐라 ClientboundTickingStepPacket) — 정지 중 남은 진행 틱 수.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record TickingStep(int steps) implements Packet {

    public static final int ID = 0x72;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { Types.writeVarInt(buf, steps); }
}
