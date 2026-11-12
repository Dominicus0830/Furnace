package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.network.chat.Component;

/** 부제목(Mojang 이름, H7c). */
public record ClientboundSetSubtitleTextPacket(Component text) implements Packet<ClientGamePacketListener>, NmsPacket {

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundSetSubtitleTextPacket(Component.furnace(text)); }
}
