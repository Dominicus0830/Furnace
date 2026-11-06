// 연결 종료 사유 — 보내면 클라이언트가 그 사유를 보이며 나간다(서버 쪽 연결은 따로 끊어야 한다. 보통은 Player.kick 을 쓴다).
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.text.Text;

public record ClientboundDisconnectPacket(Text reason) implements ClientboundPacket {

    public ClientboundDisconnectPacket {
        Objects.requireNonNull(reason, "reason");
    }

    @Override
    public PacketType<ClientboundDisconnectPacket> type() { return PacketTypes.Clientbound.DISCONNECT; }
}
