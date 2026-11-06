// 핫바 선택 칸(0~8).
package org.domi.furnace.api.packet.serverbound;

import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundSetCarriedItemPacket(int slot) implements ServerboundPacket {

    @Override
    public PacketType<ServerboundSetCarriedItemPacket> type() { return PacketTypes.Serverbound.SET_CARRIED_ITEM; }
}
