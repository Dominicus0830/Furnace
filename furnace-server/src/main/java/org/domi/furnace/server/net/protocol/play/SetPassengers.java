// S→C 승객 목록(set_passengers 0x5F): VarInt 탈것 번호, VarInt 개수 + 승객 번호들(바닐라 ClientboundSetPassengersPacket).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetPassengers(int vehicleId, int[] passengerIds) implements Packet {

    public static final int ID = 0x5F;

    /** 탈것의 지금 승객들로. */
    public static SetPassengers of(org.domi.furnace.server.entity.Entity vehicle) {
        var list = vehicle.passengers();
        int[] ids = new int[list.size()];
        for (int i = 0; i < ids.length; i++) ids[i] = list.get(i).id();
        return new SetPassengers(vehicle.id(), ids);
    }

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, vehicleId);
        Types.writeVarInt(buf, passengerIds.length);
        for (int p : passengerIds) Types.writeVarInt(buf, p);
    }
}
