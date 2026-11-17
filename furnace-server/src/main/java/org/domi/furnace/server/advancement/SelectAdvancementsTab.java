// S→C select_advancements_tab(0x4A, 바닐라 ClientboundSelectAdvancementsTabPacket) — 열 탭(없으면 null).
package org.domi.furnace.server.advancement;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundSelectAdvancementsTabPacket")
public record SelectAdvancementsTab(ResourceId tab) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_SELECT_ADVANCEMENTS_TAB;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBoolean(tab != null);
        if (tab != null) Types.writeString(buf, tab.toString());
    }
}
