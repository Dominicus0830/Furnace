// S→C 엔티티 이벤트(0x1F) — 자기 자신에게 24..28 을 보내 OP 권한 레벨(0..4)을 알린다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.protocol.Packet;

public record EntityEvent(int entityId, byte event) implements Packet {

    public static final int ID = 0x1F;

    /** 바닐라 PlayerList.sendPlayerPermissionLevel 의 이벤트 값. */
    public static EntityEvent permissionLevel(int entityId, int level) {
        return new EntityEvent(entityId, (byte) (level <= 0 ? 24 : level >= 4 ? 28 : 24 + level));
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeByte(event);
    }
}
