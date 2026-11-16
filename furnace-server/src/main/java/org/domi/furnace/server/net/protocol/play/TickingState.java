// S→C ticking_state(0x71, 바닐라 ClientboundTickingStatePacket) — 틱 속도와 정지 여부.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundTickingStatePacket")
public record TickingState(float tickRate, boolean frozen) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_TICKING_STATE;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeFloat(tickRate);
        buf.writeBoolean(frozen);
    }
}
