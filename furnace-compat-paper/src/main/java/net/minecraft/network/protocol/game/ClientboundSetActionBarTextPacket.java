package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.network.chat.Component;

/** 액션 바 글(Mojang 이름, H7c). */
public class ClientboundSetActionBarTextPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final Component text;

    public ClientboundSetActionBarTextPacket(Component text) { this.text = text; }

    public Component getText() { return text; }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundSetActionBarTextPacket(Component.furnace(text)); }
}
