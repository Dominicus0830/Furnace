// 블록좌표→RegionPos, RegionPos→스레드 인덱스 결정적 매핑. World와 스케줄러가 공유.
package org.domi.furnace.server.world;

import org.domi.furnace.api.sched.RegionPos;

public final class RegionGrid {

    private RegionGrid() {}

    /** 블록 절대좌표 → region 좌표(청크 단위 regionSize 그리드). */
    public static RegionPos regionPos(double blockX, double blockZ, int regionSize) {
        int chunkX = Math.floorDiv((int) Math.floor(blockX), 16);
        int chunkZ = Math.floorDiv((int) Math.floor(blockZ), 16);
        return new RegionPos(Math.floorDiv(chunkX, regionSize), Math.floorDiv(chunkZ, regionSize));
    }

    /** RegionPos → 스레드 인덱스 [0, n). 결정적. */
    public static int threadIndex(RegionPos pos, int n) {
        return Math.floorMod(pos.x() * 31 + pos.z(), n);
    }
}
