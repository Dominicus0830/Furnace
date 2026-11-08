package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/** 제목 시간(틱, Mojang 이름, H7c). */
public class ClientboundSetTitlesAnimationPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final int fadeIn;
    private final int stay;
    private final int fadeOut;

    public ClientboundSetTitlesAnimationPacket(int fadeIn, int stay, int fadeOut) {
        this.fadeIn = fadeIn;
        this.stay = stay;
        this.fadeOut = fadeOut;
    }

    public int getFadeIn() { return fadeIn; }

    public int getStay() { return stay; }

    public int getFadeOut() { return fadeOut; }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundSetTitlesAnimationPacket(fadeIn, stay, fadeOut); }
}
