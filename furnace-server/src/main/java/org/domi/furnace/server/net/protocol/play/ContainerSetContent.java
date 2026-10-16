// 컨테이너 전체 슬롯 내용 갱신(clientbound). 체스트(0~26)+플레이어 인벤(27~62)+carried.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ContainerSetContent(int containerId, int stateId, ItemStack[] items, ItemStack carried)
        implements Packet {

    public static final int ID = 0x13;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, containerId);
        Types.writeVarInt(buf, stateId);
        Types.writeVarInt(buf, items.length);
        for (ItemStack item : items) writeSlot(buf, item);
        writeSlot(buf, carried);
    }

    /** Slot 와이어(바닐라 ItemStack.OPTIONAL_STREAM_CODEC): 개수 0 이면 끝, 아니면 아이템 id + 컴포넌트 패치. */
    static void writeSlot(ByteBuf buf, ItemStack item) {
        org.domi.furnace.server.item.ItemStackCodecs.writeStack(buf, item);
    }
}
