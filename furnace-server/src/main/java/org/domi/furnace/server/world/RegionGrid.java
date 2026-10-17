// 블록좌표→RegionPos, RegionPos→스레드 인덱스 결정적 매핑. World와 스케줄러가 공유.
// regionSize 0 = 월드 전체가 한 region(기본 — 바닐라처럼 한 스레드가 월드를 틱해 블록·엔티티 동작이 바닐라와 같다).
// 1 이상 = 고정 격자(실험용: 경계 너머 칸은 로드되지 않은 것으로 보므로 경계에서 바닐라와 달라진다).
package org.domi.furnace.server.world;

import org.domi.furnace.api.sched.RegionPos;

public final class RegionGrid {

    private RegionGrid() {}

    /** 월드 전체 한 region 일 때의 좌표. */
    public static final RegionPos WHOLE_WORLD = new RegionPos(0, 0);

    /** 블록 절대좌표 → region 좌표(청크 단위 regionSize 그리드). */
    public static RegionPos regionPos(double blockX, double blockZ, int regionSize) {
        if (regionSize <= 0) return WHOLE_WORLD;
        int chunkX = Math.floorDiv((int) Math.floor(blockX), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(blockZ), 16);
        return new RegionPos(Math.floorDiv(chunkX, regionSize), Math.floorDiv(chunkZ, regionSize));
    }

    /** 청크 좌표 → 그 청크를 소유한 region 좌표. */
    public static RegionPos regionPosOfChunk(int chunkX, int chunkZ, int regionSize) {
        if (regionSize <= 0) return WHOLE_WORLD;
        return new RegionPos(Math.floorDiv(chunkX, regionSize), Math.floorDiv(chunkZ, regionSize));
    }

    /** RegionPos → 스레드 인덱스 [0, n). 결정적. */
    public static int threadIndex(RegionPos pos, int n) {
        return Math.floorMod(pos.x() * 31 + pos.z(), n);
    }
}
