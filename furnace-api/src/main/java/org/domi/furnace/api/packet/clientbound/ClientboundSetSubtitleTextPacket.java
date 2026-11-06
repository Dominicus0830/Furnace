// 제목 아래 부제.
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundSetSubtitleTextPacket(Text text) implements ClientboundPacket {

    public ClientboundSetSubtitleTextPacket {
        Objects.requireNonNull(text, "text");
    }

    @Override
    public PacketType<ClientboundSetSubtitleTextPacket> type() { return PacketTypes.Clientbound.SET_SUBTITLE_TEXT; }
}
