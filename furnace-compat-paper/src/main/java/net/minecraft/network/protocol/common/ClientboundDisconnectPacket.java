package net.minecraft.network.protocol.common;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;
import net.minecraft.network.chat.Component;

/** 연결 끊기(Mojang 이름, H7c). 클라이언트가 받고 스스로 끊는다. 바닐라 1.21.1 은 isTerminal 을 덮어쓰지 않는다(Packet 기본 false — 바이트코드 확인). */
public record ClientboundDisconnectPacket(Component reason) implements Packet<ClientCommonPacketListener>, NmsPacket {

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundDisconnectPacket(Component.furnace(reason)); }
}
