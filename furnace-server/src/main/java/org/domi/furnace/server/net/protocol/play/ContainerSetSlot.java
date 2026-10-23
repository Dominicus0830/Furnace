// 컨테이너 칸 하나 갱신(clientbound container_set_slot 0x15): 창 id(-1 = 커서), 상태 번호, 칸 번호, 아이템.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ContainerSetSlot(int containerId, int stateId, int slot, ItemStack item) implements Packet {

    public static final int ID = 0x15;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(containerId);
        Types.writeVarInt(buf, stateId);
        buf.writeShort(slot);
        ContainerSetContent.writeSlot(buf, item);
    }
}
