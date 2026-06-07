// S→C 플레이어 레지스트리 등록(add_player + update_listed). 엔티티 스폰 전 필수(1.20.2+).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerInfoUpdate(UUID uuid, String name) implements Packet {

    public static final int ID = 0x3E;
    private static final int ACTIONS = 0x01 | 0x08; // add_player | update_listed

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(ACTIONS);
        Types.writeVarInt(buf, 1);     // 플레이어 1명
        Types.writeUuid(buf, uuid);
        Types.writeString(buf, name);  // add_player: name
        Types.writeVarInt(buf, 0);     // add_player: property count
        buf.writeBoolean(true);        // update_listed
    }
}
