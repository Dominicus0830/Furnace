// 제목 지우기. resetTimes 면 제목 시간도 기본값으로 되돌린다.
package org.domi.furnace.api.packet.clientbound;

import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundClearTitlesPacket(boolean resetTimes) implements ClientboundPacket {

    @Override
    public PacketType<ClientboundClearTitlesPacket> type() { return PacketTypes.Clientbound.CLEAR_TITLES; }
}
