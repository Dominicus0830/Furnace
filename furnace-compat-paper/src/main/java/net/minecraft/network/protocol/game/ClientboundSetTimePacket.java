package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/**
 * 월드 시간(Mojang 이름, H7c). 1.21.1 바닐라처럼 하루 순환이 꺼져 있으면 하루 시간을 음수로 바꿔 보낸다(0 은 -1) — 클라이언트는 음수를 멈춤으로 읽는다.
 */
public class ClientboundSetTimePacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final long gameTime;
    private final long dayTime;

    public ClientboundSetTimePacket(long gameTime, long dayTime, boolean daylightCycle) {
        this.gameTime = gameTime;
        long d = dayTime;
        if (!daylightCycle) {
            d = -d;
            if (d == 0L) d = -1L;
        }
        this.dayTime = d;
    }

    public long getGameTime() { return gameTime; }

    public long getDayTime() { return dayTime; }

    @Override
    public ClientboundPacket toFurnace() { return new org.domi.furnace.api.packet.clientbound.ClientboundSetTimePacket(gameTime, dayTime); }
}
