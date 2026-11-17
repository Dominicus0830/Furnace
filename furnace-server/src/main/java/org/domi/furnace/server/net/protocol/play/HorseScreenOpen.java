// 말 인벤토리 창 열기(clientbound horse_screen_open): 창 번호(부호 없는 바이트)·인벤토리 열 수(VarInt)·말 엔티티 번호(Int).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundHorseScreenOpenPacket")
public record HorseScreenOpen(int containerId, int inventoryColumns, int entityId) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_HORSE_SCREEN_OPEN;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(containerId);
        Types.writeVarInt(buf, inventoryColumns);
        buf.writeInt(entityId);
    }
}
