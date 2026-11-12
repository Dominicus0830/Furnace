// 청크가 로드돼 설치된 직후 — 그 청크를 가진 region 스레드에서 불린다. chunk() 로 청크 핸들을 얻는다.
package org.domi.furnace.api.event.world;

public final class ChunkLoadEvent extends WorldEvent {

    private final int chunkX, chunkZ;
    private final boolean newChunk;

    public ChunkLoadEvent(String world, int chunkX, int chunkZ) { this(world, chunkX, chunkZ, false); }

    public ChunkLoadEvent(String world, int chunkX, int chunkZ, boolean newChunk) {
        super(world);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.newChunk = newChunk;
    }

    /** 이 청크가 이번에 처음 만들어졌는가(저장된 청크를 읽은 것이 아니라 생성기가 새로 만들었다). Paper 의 ChunkLoadEvent#isNewChunk 와 같다. */
    public boolean newChunk() { return newChunk; }

    public int chunkX() { return chunkX; }

    public int chunkZ() { return chunkZ; }

    /** 이 청크의 핸들(월드가 없으면 null). */
    public org.domi.furnace.api.world.Chunk chunk() {
        var w = org.domi.furnace.api.Furnace.server().world(world());
        return w == null ? null : w.chunkAt(chunkX, chunkZ);
    }
}
