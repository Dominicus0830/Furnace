// 화면 가운데 큰 제목.
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundSetTitleTextPacket(Text text) implements ClientboundPacket {

    public ClientboundSetTitleTextPacket {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public PacketType<ClientboundSetTitleTextPacket> type() { return PacketTypes.Clientbound.SET_TITLE_TEXT; }
}
