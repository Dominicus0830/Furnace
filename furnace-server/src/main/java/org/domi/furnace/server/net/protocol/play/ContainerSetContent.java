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

    /**
     * Slot 와이어: count==0이면 끝. 아니면 itemId 뒤로 numComponentsToAdd/Remove(§2.3, M4c-3 범위 —
     * components 없는 아이템만 정확 처리). components blob이 있으면(0x32에서 캡처한 원본 바이트) 그대로 재사용,
     * 없으면 numAdd=0/numRemove=0 두 바이트.
     */
    static void writeSlot(ByteBuf buf, ItemStack item) {
        if (item == null || item.isEmpty()) {
            Types.writeVarInt(buf, 0);
            return;
        }
        Types.writeVarInt(buf, item.count());
        Types.writeVarInt(buf, item.itemId());
        byte[] comp = item.components();
        if (comp != null) {
            buf.writeBytes(comp);
        } else {
            Types.writeVarInt(buf, 0);
            Types.writeVarInt(buf, 0);
        }
    }
}
