package net.minecraft.server.level;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;

/**
 * 청크 지도(Mojang 이름 shim, N2b-6): {@code ServerChunkCache.chunkMap}. 청크 자리 찾기와 그 청크를 받는 플레이어 목록만 있다(엔티티 추적기 등은 N3a).
 */
public class ChunkMap {

    public static final int MIN_VIEW_DISTANCE = 2;
    public static final int MAX_VIEW_DISTANCE = 32;

    private final ServerLevel level;

    ChunkMap(ServerLevel level) { this.level = level; }

    /** 로드된 청크의 자리(바닐라: 보이는 청크 자리 표). 아니면 null. 아무 스레드. */
    public ChunkHolder getVisibleChunkIfPresent(long pos) {
        int x = ChunkPos.getX(pos), z = ChunkPos.getZ(pos);
        return level.getChunkSource().hasChunk(x, z) ? new ChunkHolder(level, new ChunkPos(x, z)) : null;
    }

    /** {@link #getVisibleChunkIfPresent} 와 같다(Furnace 는 갱신 중 자리 표를 따로 두지 않는다). */
    public ChunkHolder getUpdatingChunkIfPresent(long pos) { return getVisibleChunkIfPresent(pos); }

    /**
     * 그 청크를 받고 있는 플레이어. Furnace 는 플레이어마다 받은 청크 목록을 내주지 않아, 그 월드에서 청크까지의 체비셰프 거리가 그 플레이어의 시야 거리
     * 이하인 플레이어로 본다(가정 — 바닐라는 원형 시야 + 실제로 보낸 청크만). onlyOnWatchDistanceEdge 면 거리가 시야 거리와 같은 플레이어만.
     */
    public List<ServerPlayer> getPlayers(ChunkPos pos, boolean onlyOnWatchDistanceEdge) {
        List<ServerPlayer> out = new ArrayList<>();
        for (var p : level.getWorld().getPlayers()) {
            var loc = p.getLocation();
            int d = Math.max(Math.abs((loc.getBlockX() >> 4) - pos.x), Math.abs((loc.getBlockZ() >> 4) - pos.z));
            int view = p.getViewDistance();
            if (onlyOnWatchDistanceEdge ? d == view : d <= view) out.add(((org.bukkit.craftbukkit.entity.CraftPlayer) p).getHandle());
        }
        return out;
    }

    /** 바닐라: 그 청크들의 바이옴을 다시 보낸다 — Furnace 는 청크를 통째로 다시 보낸다(Paper World#refreshChunk). */
    public void resendBiomesForChunks(List<net.minecraft.world.level.chunk.ChunkAccess> chunks) {
        for (var c : chunks) level.getWorld().refreshChunk(c.getPos().x, c.getPos().z);
    }
}
