// S→C ticking_state(0x71, 바닐라 ClientboundTickingStatePacket) — 틱 속도와 정지 여부.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record TickingState(float tickRate, boolean frozen) implements Packet {

    public static final int ID = 0x71;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeFloat(tickRate);
        buf.writeBoolean(frozen);
    }
}
