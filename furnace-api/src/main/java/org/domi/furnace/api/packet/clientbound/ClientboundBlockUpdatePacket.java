// 블록 한 칸이 바뀌었다고 알린다. 서버 월드는 바뀌지 않는다 — 보낸 플레이어에게만 보이는 "가상 블록"을 만들 때 쓴다.
// block 은 이름 기반 BlockData 라 버전과 무관하다(서버가 그 버전의 블록 상태 번호로 바꾼다). 없는 블록이면 보낼 때 IllegalArgumentException.
package org.domi.furnace.api.packet.clientbound;

import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public record ClientboundBlockUpdatePacket(BlockPos pos, BlockData block) implements ClientboundPacket {

    public ClientboundBlockUpdatePacket {
        Objects.requireNonNull(pos, "pos");
        Objects.requireNonNull(block, "block");
    }

    public ClientboundBlockUpdatePacket withBlock(BlockData b) { return new ClientboundBlockUpdatePacket(pos, b); }

    @Override
    public PacketType<ClientboundBlockUpdatePacket> type() { return PacketTypes.Clientbound.BLOCK_UPDATE; }
}
