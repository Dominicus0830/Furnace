// 월드 시간. dayTime 이 음수면 클라이언트가 하루 시간을 멈춘다(바닐라 doDaylightCycle=false 표시).
package org.domi.furnace.api.packet.clientbound;

import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundSetTimePacket(long gameTime, long dayTime) implements ClientboundPacket {

    @Override
    public PacketType<ClientboundSetTimePacket> type() { return PacketTypes.Clientbound.SET_TIME; }
}
