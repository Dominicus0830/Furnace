package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.network.chat.Component;

/** 시스템 채팅(Mojang 이름, H7c). overlay=true 면 액션 바에 뜬다. */
public record ClientboundSystemChatPacket(Component content, boolean overlay) implements Packet<ClientGamePacketListener>, NmsPacket {

    @Override
    public ClientboundPacket toFurnace() {
        return new org.domi.furnace.api.packet.clientbound.ClientboundSystemChatPacket(Component.furnace(content), overlay);
    }
}
