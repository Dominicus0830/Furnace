// LEGACY-ORACLE since 7d55ec3a: ChunkColumn 칸별 기둥 탐색·rebuildHeightmapsAfterSurface (0697ad69 섹션 단위 빠른 경로 이전)
// 옮길 때 바꾼 것은 클래스 밖에서 돌게 하는 부분뿐이다: 인스턴스 필드 → 인자(ChunkColumn), private 판정 함수 → 넘겨받는 술어,
// sectionBlocks(sec)[…] → getBlock(…)(같은 칸을 읽는다). 탐색 순서·반환 값 계산은 그 커밋 그대로다.
package org.domi.furnace.server.identity.legacy.chunkio;

import java.util.function.IntPredicate;
import org.domi.furnace.server.identity.LegacyOracle;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;

@LegacyOracle(since = "7d55ec3a",
        copyOf = "ChunkColumn#motionBlockingNoLeavesBlockY, #recomputeMotionBlockingHeight, #recomputeOceanFloorHeight, #rebuildHeightmapsAfterSurface")
public final class LegacyColumnScans {

    private LegacyColumnScans() {}

    /** 위에서 아래로 칸별 탐색. worldY 면 월드 y(없으면 minY-1), 아니면 높이값(y-minY+1, 없으면 0). */
    public static int top(ChunkColumn col, int x, int z, IntPredicate p, boolean worldY) {
        int minY = ChunkColumn.MIN_Y;
        for (int y = minY + ChunkColumn.SECTION_COUNT * 16 - 1; y >= minY; y--) {
            if (p.test(col.getBlock(x, y, z))) return worldY ? y : (y - minY) + 1;
        }
        return worldY ? minY - 1 : 0;
    }

    /** 수정 전 rebuildHeightmapsAfterSurface 의 기둥 계산. {surface[256], oceanFloor[256]} 높이값. */
    public static int[][] rebuild(ChunkColumn col, IntPredicate isOceanFloor) {
        int[] surface = new int[256], ocean = new int[256];
        for (int lz = 0; lz < 16; lz++) for (int lx = 0; lx < 16; lx++) {
            int sv = 0, ov = 0;
            for (int sec = ChunkColumn.SECTION_COUNT - 1; sec >= 0 && (sv == 0 || ov == 0); sec--) {
                for (int ly = 15; ly >= 0; ly--) {
                    int st = col.getBlock(lx, ChunkColumn.MIN_Y + sec * 16 + ly, lz);
                    int y1 = sec * 16 + ly + 1;
                    if (sv == 0 && !ChunkSection.isAir(st)) sv = y1;
                    if (ov == 0 && isOceanFloor.test(st)) ov = y1;
                    if (sv != 0 && ov != 0) break;
                }
            }
            surface[lz * 16 + lx] = sv;
            ocean[lz * 16 + lx] = ov;
        }
        return new int[][]{surface, ocean};
    }
}
