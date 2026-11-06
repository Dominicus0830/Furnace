// 장비 갱신(clientbound set_equipment 0x5B): 엔티티, [칸 번호(뒤에 더 있으면 0x80 켬), 아이템]....
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.entity.EquipmentSlot;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetEquipment(int entityId, List<Entry> slots) implements Packet {

    public static final int ID = 0x5B;

    public record Entry(EquipmentSlot slot, ItemStack stack) {}

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        for (int i = 0; i < slots.size(); i++) {
            Entry e = slots.get(i);
            int b = e.slot().ordinal();
            if (i != slots.size() - 1) b |= 0x80;
            buf.writeByte(b);
            ContainerSetContent.writeSlot(buf, e.stack());
        }
    }
}
