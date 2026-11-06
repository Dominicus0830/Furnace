// BlockPos.withinManhattan 재현 검증 — 상자 전체를 중복 없이, 맨해튼 거리 비감소 순으로 방문해야 한다
package org.domi.furnace.server.world.gen.feature;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BlockPosIterTest {

    @Test
    void visitsWholeBoxOnceInManhattanOrder() {
        int ox = 5, oy = -3, oz = 9;
        int rx = 3, ry = 0, rz = 2;
        List<int[]> seen = new ArrayList<>();
        BlockPosIter.withinManhattan(ox, oy, oz, rx, ry, rz, (x, y, z) -> {
            seen.add(new int[]{x, y, z});
            return true;
        });
        assertEquals((2 * rx + 1) * (2 * ry + 1) * (2 * rz + 1), seen.size());
        Set<String> unique = new HashSet<>();
        int last = 0;
        for (int[] p : seen) {
            assertTrue(unique.add(p[0] + "," + p[1] + "," + p[2]));
            assertTrue(Math.abs(p[0] - ox) <= rx && Math.abs(p[1] - oy) <= ry && Math.abs(p[2] - oz) <= rz);
            int d = BlockPosIter.distManhattan(p[0], p[1], p[2], ox, oy, oz);
            assertTrue(d >= last, "거리가 줄어들면 안 된다");
            last = d;
        }
        assertArrayEquals(new int[]{ox, oy, oz}, seen.get(0));
        // 깊이 1 의 첫 원소는 x=-1 (z 오프셋 0 이라 대칭 쌍 없음)
        assertArrayEquals(new int[]{ox - 1, oy, oz}, seen.get(1));
    }

    @Test
    void visitorCanStopEarly() {
        int[] n = {0};
        BlockPosIter.withinManhattan(0, 0, 0, 4, 4, 4, (x, y, z) -> ++n[0] < 5);
        assertEquals(5, n[0]);
    }
}
