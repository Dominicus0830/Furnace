// 블록에 대고 우클릭. cursor 는 클릭한 칸 안의 위치(0~1), insideBlock 은 머리가 블록 안에 있을 때.
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.entity.Hand;
import org.domi.furnace.api.world.BlockFace;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundUseItemOnPacket(Hand hand, BlockPos pos, BlockFace face, float cursorX, float cursorY, float cursorZ,
                                         boolean insideBlock, int sequence) implements ServerboundPacket {

    public ServerboundUseItemOnPacket {
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
    }

    @Override
    public PacketType<ServerboundUseItemOnPacket> type() { return PacketTypes.Serverbound.USE_ITEM_ON; }
}
