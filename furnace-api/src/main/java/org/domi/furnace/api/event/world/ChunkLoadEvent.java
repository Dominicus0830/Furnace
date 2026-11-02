// 청크가 로드돼 설치된 직후 — 그 청크를 가진 region 스레드에서 불린다. 좌표만 담는다(청크 객체는 다음 단계).
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
}
