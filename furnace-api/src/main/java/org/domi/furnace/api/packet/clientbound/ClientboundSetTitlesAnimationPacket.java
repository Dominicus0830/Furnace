// 제목 시간(틱): 나타남·유지·사라짐.
package org.domi.furnace.api.packet.clientbound;

import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundSetTitlesAnimationPacket(int fadeIn, int stay, int fadeOut) implements ClientboundPacket {

    @Override
    public PacketType<ClientboundSetTitlesAnimationPacket> type() { return PacketTypes.Clientbound.SET_TITLES_ANIMATION; }
}
