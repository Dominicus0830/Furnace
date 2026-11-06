// S→C 엔티티 줍기 동작(take_item_entity 0x6F): 주운 엔티티 id, 주운 엔티티 id, 개수. 클라이언트가 날아가는 모습과 소리를 낸다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record TakeItemEntity(int itemId, int playerId, int amount) implements Packet {

    public static final int ID = 0x6F;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, itemId);
        Types.writeVarInt(buf, playerId);
        Types.writeVarInt(buf, amount);
    }
}
