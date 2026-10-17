// region 스케줄러가 작업을 보낼 곳: 청크 좌표. region 은 동적(가까운 청크끼리 묶임)이라 그 청크를 지금 가진 region 에서 돈다.
package org.domi.furnace.api.sched;

public record RegionPos(int x, int z) {

    /** 블록 좌표가 든 청크. */
    public static RegionPos ofBlock(double blockX, double blockZ) {
        return new RegionPos(Math.floorDiv((int) Math.floor(blockX), 16), Math.floorDiv((int) Math.floor(blockZ), 16));
    }
}
