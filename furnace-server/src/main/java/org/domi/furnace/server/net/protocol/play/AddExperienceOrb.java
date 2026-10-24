// 경험치 구슬 스폰(clientbound add_experience_orb 0x02): 엔티티 번호, 추적기 기준 위치, 구슬 하나의 값(short).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record AddExperienceOrb(int entityId, double x, double y, double z, int value) implements Packet {

    public static final int ID = 0x02;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeShort(value);
    }
}
