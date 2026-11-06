// 블록 캐기 시작·취소·완료, 아이템 버리기, 사용 끝내기, 손 바꾸기(F). sequence 는 클라이언트 예측 번호(서버가 ack 로 돌려준다).
package org.domi.furnace.api.packet.serverbound;

import java.util.Objects;
import org.domi.furnace.api.world.BlockFace;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundPlayerActionPacket(Action action, BlockPos pos, BlockFace face, int sequence) implements ServerboundPacket {

    public enum Action {
        START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK, DROP_ALL_ITEMS, DROP_ITEM, RELEASE_USE_ITEM, SWAP_ITEM_WITH_OFFHAND
    }

    public ServerboundPlayerActionPacket {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(face, "face");
    }

    @Override
    public PacketType<ServerboundPlayerActionPacket> type() { return PacketTypes.Serverbound.PLAYER_ACTION; }
}
