// 청크가 로드돼 설치된 직후 — 그 청크를 가진 region 스레드에서 불린다. chunk() 로 청크 핸들을 얻는다.
package org.domi.furnace.api.event.world;

public final class ChunkLoadEvent extends WorldEvent {

    private final int chunkX, chunkZ;

    public ChunkLoadEvent(String world, int chunkX, int chunkZ) {
        super(world);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() { return chunkX; }

    public int chunkZ() { return chunkZ; }

    /** 이 청크의 핸들(월드가 없으면 null). */
    public org.domi.furnace.api.world.Chunk chunk() {
        var w = org.domi.furnace.api.Furnace.server().world(world());
        return w == null ? null : w.chunkAt(chunkX, chunkZ);
    }
}
