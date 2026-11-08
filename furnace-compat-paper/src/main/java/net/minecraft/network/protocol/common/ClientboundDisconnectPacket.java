package net.minecraft.network.protocol.common;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.network.chat.Component;

/** 연결 끊기(Mojang 이름, H7c). 클라이언트가 받고 스스로 끊는다. */
public record ClientboundDisconnectPacket(Component reason) implements Packet<ClientCommonPacketListener>, NmsPacket {

    @Override
    public boolean isTerminal() { return true; }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundDisconnectPacket(Component.furnace(reason)); }
}
