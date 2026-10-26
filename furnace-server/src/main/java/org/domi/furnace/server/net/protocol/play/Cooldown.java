// S→C 아이템 재사용 대기(ClientboundCooldownPacket): 아이템 레지스트리 번호 + 틱 수(0 이면 끝남).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record Cooldown(int itemId, int duration) implements Packet {

    public static final int ID = 0x17;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, itemId);
        Types.writeVarInt(buf, duration);
    }
}
