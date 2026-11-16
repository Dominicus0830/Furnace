// S→C 시뮬레이션 거리(0x62): VarInt.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundSetSimulationDistancePacket")
public record SetSimulationDistance(int distance) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_SET_SIMULATION_DISTANCE;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { Types.writeVarInt(buf, distance); }
}
