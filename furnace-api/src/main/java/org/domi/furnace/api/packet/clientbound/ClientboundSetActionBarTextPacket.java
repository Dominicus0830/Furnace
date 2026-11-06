// 액션바 글자(핫바 위).
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundSetActionBarTextPacket(Text text) implements ClientboundPacket {

    public ClientboundSetActionBarTextPacket {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public PacketType<ClientboundSetActionBarTextPacket> type() { return PacketTypes.Clientbound.SET_ACTION_BAR_TEXT; }
}
