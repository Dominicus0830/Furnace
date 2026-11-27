package net.minecraft.network.protocol.game;

import net.minecraft.network.protocol.Packet;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.compat.nms.NmsPacket;

/**
 * 청크와 빛 패킷 shim(Mojang 이름, T-182, ProtocolLib MAP_CHUNK). 바닐라 1.21.1 과 같은 모양 — 청크 좌표, 청크 자료, 빛 자료.
 * 서버가 보내는 청크를 패킷 라이브러리 리스너가 보고 고칠 수 있다(블록 숨기기 등). 청크 자료는 {@link ClientboundLevelChunkPacketData}.
 */
class ClientboundLevelChunkWithLightPacket implements NmsPacket {

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

    // N2b-6: 청크 보기로 만드는 바닐라 생성자. Furnace 는 청크 본문·빛 바이트를 shim 에서 만들지 않고, 이 패킷을 보낼 때 그 청크를 보고 있는 플레이어에게
    // 서버가 지금 상태의 청크를 통째로 다시 보낸다(Paper World#refreshChunk — 같은 패킷 객체를 여러 플레이어에게 보내도 한 번, PacketDelivery). 그래서
    // getChunkData()·getLightData() 는 null 이다. 빛 비트 집합 인자와 Paper 의 엑스레이 막기 인자는 쓰지 않는다(서버가 늘 전부 보낸다).
    // 그 청크는 패킷 밖 표(ChunkRefreshPackets)에 둔다 — 패킷 필드는 바닐라 모양 그대로여야 패킷 라이브러리의 필드 순번이 맞는다.

    public ClientboundLevelChunkWithLightPacket(net.minecraft.world.level.chunk.LevelChunk chunk, net.minecraft.world.level.lighting.LevelLightEngine lightEngine,
            java.util.BitSet skyChangedLightSectionFilter, java.util.BitSet blockChangedLightSectionFilter) {
        this(chunk.getPos().x, chunk.getPos().z, null, null);
        org.domi.furnace.compat.nms.ChunkRefreshPackets.register(this, chunk);
    }

    public ClientboundLevelChunkWithLightPacket(net.minecraft.world.level.chunk.LevelChunk chunk, net.minecraft.world.level.lighting.LevelLightEngine lightEngine,
            java.util.BitSet skyChangedLightSectionFilter, java.util.BitSet blockChangedLightSectionFilter, boolean modifyBlocks) {
        this(chunk, lightEngine, skyChangedLightSectionFilter, blockChangedLightSectionFilter);
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
