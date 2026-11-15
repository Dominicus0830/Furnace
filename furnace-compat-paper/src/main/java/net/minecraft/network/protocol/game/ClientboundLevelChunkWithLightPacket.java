package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/**
 * 청크와 빛 패킷 shim(Mojang 이름, T-182, ProtocolLib MAP_CHUNK). 바닐라 1.21.1 과 같은 모양 — 청크 좌표, 청크 자료, 빛 자료.
 * 서버가 보내는 청크를 패킷 라이브러리 리스너가 보고 고칠 수 있다(블록 숨기기 등). 청크 자료는 {@link ClientboundLevelChunkPacketData}.
 */
public class ClientboundLevelChunkWithLightPacket implements Packet<ClientGamePacketListener>, NmsPacket {

    private final int x;
    private final int z;
    private final ClientboundLevelChunkPacketData chunkData;
    private final ClientboundLightUpdatePacketData lightData;

    /** 이 모듈 전용: 값으로. 패킷 라이브러리가 빈 패킷을 만들 때도 쓴다(필드는 그 뒤 라이브러리가 채운다). */
    private ClientboundLevelChunkWithLightPacket(int x, int z, ClientboundLevelChunkPacketData chunkData, ClientboundLightUpdatePacketData lightData) {
        this.x = x;
        this.z = z;
        this.chunkData = chunkData;
        this.lightData = lightData;
    }

    /** 이 모듈 전용. */
    public static ClientboundLevelChunkWithLightPacket of(int x, int z, ClientboundLevelChunkPacketData chunkData, ClientboundLightUpdatePacketData lightData) {
        return new ClientboundLevelChunkWithLightPacket(x, z, chunkData, lightData);
    }

    public int getX() { return x; }

    public int getZ() { return z; }

    public ClientboundLightUpdatePacketData getLightData() { return lightData; }

    public ClientboundLevelChunkPacketData getChunkData() { return chunkData; }

    @Override
    public ClientboundPacket toFurnace() { return org.domi.furnace.compat.nms.ChunkPacketBridge.toFurnace(this); }

    @Override
    public String toString() { return "ClientboundLevelChunkWithLightPacket{x=" + x + ", z=" + z + "}"; }
}
