// 플레이어 정보 제거(player_info_remove) — 탭 목록·스킨 항목을 지운다. 그 uuid 의 엔티티가 화면에 있으면 먼저 remove_entities 로 치운다.
package org.domi.furnace.api.packet.clientbound;

import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundPlayerInfoRemovePacket(List<UUID> uuids) implements ClientboundPacket {

    public ClientboundPlayerInfoRemovePacket {
        uuids = List.copyOf(uuids);
    }

    public static ClientboundPlayerInfoRemovePacket of(UUID... uuids) { return new ClientboundPlayerInfoRemovePacket(List.of(uuids)); }

    @Override
    public PacketType<ClientboundPlayerInfoRemovePacket> type() { return PacketTypes.Clientbound.PLAYER_INFO_REMOVE; }
}
