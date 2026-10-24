// 효과 사라짐(clientbound remove_mob_effect 0x43): 엔티티, 효과 번호.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RemoveMobEffect(int entityId, int effectId) implements Packet {

    public static final int ID = 0x43;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeVarInt(buf, effectId);
    }
}
