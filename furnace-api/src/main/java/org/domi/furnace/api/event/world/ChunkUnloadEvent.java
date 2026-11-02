// 청크가 메모리에서 내려간 직후(저장 요청은 이미 보냈다) — 그 청크를 가진 region 스레드에서 불린다. 좌표만 담는다(청크 객체는 다음 단계).
package org.domi.furnace.api.event.world;

public final class ChunkUnloadEvent extends WorldEvent {

    private final int chunkX, chunkZ;

    public ChunkUnloadEvent(String world, int chunkX, int chunkZ) {
        super(world);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public int chunkX() { return chunkX; }

    public int chunkZ() { return chunkZ; }
}
