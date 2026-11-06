// 표지판 글 편집 완료: 위치, 앞면인가, 네 줄(줄마다 최대 384자).
package org.domi.furnace.api.packet.serverbound;

import java.util.List;
import java.util.Objects;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundSignUpdatePacket(BlockPos pos, boolean frontText, List<String> lines) implements ServerboundPacket {

    public ServerboundSignUpdatePacket {
        Objects.requireNonNull(pos, "pos");
        lines = List.copyOf(lines);
        if (lines.size() != 4) throw new IllegalArgumentException("줄은 4개: " + lines.size());
    }

    @Override
    public PacketType<ServerboundSignUpdatePacket> type() { return PacketTypes.Serverbound.SIGN_UPDATE; }
}
