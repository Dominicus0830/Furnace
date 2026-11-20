package ca.spottedleaf.moonrise.patches.chunk_system.level.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * Paper(Moonrise) 월드 엔티티 찾기 자리(N2b-6 — 플러그인이 쓰는 것만): {@code Level#moonrise$getEntityLookup()}. 청크별 엔티티 묶음
 * ({@link #getChunk})만 있고, 엔티티 넣기({@link #addNewEntity})는 아직 없다.
 */
public class EntityLookup {

    private final ServerLevel level;

    public EntityLookup(ServerLevel level) { this.level = level; }

    /** 그 청크가 로드돼 있으면 그 청크의 엔티티 묶음, 아니면 null(Paper: 엔티티 묶음이 없으면 null). */
    public ChunkEntitySlices getChunk(int chunkX, int chunkZ) {
        return level.getChunkSource().hasChunk(chunkX, chunkZ) ? new ChunkEntitySlices(level, chunkX, chunkZ) : null;
    }

    /**
     * Paper: 이벤트 없이 엔티티를 월드에 넣는다(T-212 — FAWE 가 엔티티 이벤트를 끈 붙여 넣기에 쓴다). {@code EntityType#create} 로 만든 엔티티만 넣고,
     * 같은 UUID 가 이미 있거나 넣기 전에 지웠거나 이미 월드에 있으면 거짓. 스폰 이유·generation 필드는 건드리지 않는다(Paper 와 같다).
     */
    public boolean addNewEntity(Entity entity) { return entity.furnaceAddToWorld(level, null, false); }
}
