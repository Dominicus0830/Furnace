package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.network.chat.Component;

/** 제목(Mojang 이름, H7c). */
public class ClientboundSetTitleTextPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final Component text;

    public ClientboundSetTitleTextPacket(Component text) { this.text = text; }

    public Component getText() { return text; }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundSetTitleTextPacket(Component.furnace(text)); }
}
