package net.minecraft.world.level.chunk;

import net.minecraft.world.level.chunk.status.ChunkStatus;

/** 청크 공급원(Mojang 이름, N2a). 청크를 동기로 불러오지 않는다 — {@link net.minecraft.server.level.ServerChunkCache} 참고. */
public abstract class ChunkSource {

    protected ChunkSource() {}

    /** 로드돼 있으면 그 청크, 아니면 create 가 false 면 null, true 면 IllegalStateException(Furnace 는 동기로 불러오지 않는다). */
    public abstract ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create);

    public LevelChunk getChunk(int x, int z, boolean create) { return (LevelChunk) getChunk(x, z, ChunkStatus.FULL, create); }

    public LevelChunk getChunkNow(int x, int z) { return getChunk(x, z, false); }

    public boolean hasChunk(int x, int z) { return getChunk(x, z, ChunkStatus.FULL, false) != null; }

    /** 로드된 청크 수. */
    public abstract int getLoadedChunksCount();
}
