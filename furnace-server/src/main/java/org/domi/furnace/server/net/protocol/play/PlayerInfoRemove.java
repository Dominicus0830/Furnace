// S→C 플레이어 목록(탭)서 제거. 엔티티 디스폰 때 PlayerInfoUpdate로 넣은 항목을 같이 뺀다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerInfoRemove(List<UUID> uuids) implements Packet {

    public static final int ID = 0x3D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, uuids.size());
        for (UUID u : uuids) Types.writeUuid(buf, u);
    }
}
