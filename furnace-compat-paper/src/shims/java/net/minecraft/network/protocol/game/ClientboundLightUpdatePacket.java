package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/** 빛 갱신 패킷 shim(Mojang 이름, T-182, ProtocolLib LIGHT_UPDATE). 바닐라 1.21.1 과 같은 모양 — 청크 좌표와 빛 자료. */
class ClientboundLightUpdatePacket implements NmsPacket {

    private final int x;
    private final int z;
    private final ClientboundLightUpdatePacketData lightData;

    /** 이 모듈 전용: 값으로. 패킷 라이브러리가 빈 패킷을 만들 때도 쓴다. */
    private ClientboundLightUpdatePacket(int x, int z, ClientboundLightUpdatePacketData lightData) {
        this.x = x;
        this.z = z;
        this.lightData = lightData;
    }

    /** 이 모듈 전용. */
    public static ClientboundLightUpdatePacket of(int x, int z, ClientboundLightUpdatePacketData lightData) {
        return new ClientboundLightUpdatePacket(x, z, lightData);
    }

    public int getX() { return x; }

    public int getZ() { return z; }

    public ClientboundLightUpdatePacketData getLightData() { return lightData; }

    @Override
    public ClientboundPacket toFurnace() { return org.domi.furnace.compat.nms.ChunkPacketBridge.toFurnace(this); }

    @Override
    public String toString() { return "ClientboundLightUpdatePacket{x=" + x + ", z=" + z + "}"; }
}
