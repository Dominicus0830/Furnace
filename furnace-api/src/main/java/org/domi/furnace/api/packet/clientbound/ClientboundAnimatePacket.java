// 엔티티 동작 애니메이션(팔 휘두르기·치명타 입자 등).
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundAnimatePacket(int entityId, Animation animation) implements ClientboundPacket {

    public enum Animation { SWING_MAIN_HAND, WAKE_UP, SWING_OFF_HAND, CRITICAL_HIT, MAGIC_CRITICAL_HIT }

    public ClientboundAnimatePacket {
        Objects.requireNonNull(animation, "animation");
    }

    @Override
    public PacketType<ClientboundAnimatePacket> type() { return PacketTypes.Clientbound.ANIMATE; }
}
