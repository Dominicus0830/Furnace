// 손에 든 아이템 사용(허공 우클릭). yaw·pitch 는 그때 시선(도).
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundUseItemPacket(Hand hand, int sequence, float yaw, float pitch) implements ServerboundPacket {

    public ServerboundUseItemPacket {
        Objects.requireNonNull(hand, "hand");
    }

    @Override
    public PacketType<ServerboundUseItemPacket> type() { return PacketTypes.Serverbound.USE_ITEM; }
}
