package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/** 제목 지우기(Mojang 이름, H7c). resetTimes 면 시간도 기본값으로. */
public class ClientboundClearTitlesPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final boolean resetTimes;

    public ClientboundClearTitlesPacket(boolean resetTimes) { this.resetTimes = resetTimes; }

    public boolean shouldResetTimes() { return resetTimes; }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundClearTitlesPacket(resetTimes); }
}
