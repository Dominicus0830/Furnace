// T-051: 창 한 번 읽기(closestVillageSection)가 옛 구현(섹션마다 sectionsToVillage)과 같은 답을 내는지 무작위 POI 배치로 대조한다.
package org.domi.furnace.server.world.poi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.Test;

class ClosestVillageSectionTest {
    private static final int MIN_SECTION = -4, SECTIONS = 24;

    /** 옛 구현(VillageBoundRandomStroll.findSectionClosestToVillage)을 그대로 남긴 오라클. */
    private static int[] old(PoiManager poi, int[] sp, int radius) {
        int cur = poi.sectionsToVillage(sp[0], sp[1], sp[2]);
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int z = sp[2] - radius; z <= sp[2] + radius; z++)
            for (int y = sp[1] - radius; y <= sp[1] + radius; y++)
                for (int x = sp[0] - radius; x <= sp[0] + radius; x++) {
                    int d = poi.sectionsToVillage(x, y, z);
                    if (d < cur && d < bestD) {
                        bestD = d;
                        best = new int[] {x, y, z};
                    }
                }
        return best == null ? sp : best;
    }

    private static PoiManager randomWorld(Random r, int span, int pois, Map<Long, PoiChunk> cols) {
        PoiManager poi = new PoiManager((cx, cz) -> cols.get(((long) cx << 32) | (cz & 0xFFFFFFFFL)));
        for (int cx = -span; cx <= span; cx++)
            for (int cz = -span; cz <= span; cz++)
                if (r.nextInt(10) != 0) cols.put(((long) cx << 32) | (cz & 0xFFFFFFFFL), new PoiChunk(MIN_SECTION, SECTIONS));   // 열 없음 10%
        PoiType[] types = {PoiTypes.MEETING, PoiTypes.HOME, PoiTypes.LIBRARIAN, PoiTypes.LIGHTNING_ROD, PoiTypes.BEEHIVE};
        List<Pos> placed = new ArrayList<>();
        for (int i = 0; i < pois; i++) {
            Pos p = new Pos(r.nextInt(span * 32 + 16) - span * 16 - 8, r.nextInt(24 * 16) - 64, r.nextInt(span * 32 + 16) - span * 16 - 8);
            PoiType t = types[r.nextInt(types.length)];
            poi.add(p, t);
            if (poi.getType(p) == t) placed.add(p);
        }
        for (Pos p : placed) if (r.nextBoolean()) poi.take(t -> true, (t, q) -> q.equals(p), p, 0);   // 점유: 마을 중심 조건
        return poi;
    }

    @Test
    void sameAsOldForRandomLayouts() {
        Random r = new Random(0x7051);
        int villagePicks = 0, total = 0;
        for (int round = 0; round < 60; round++) {
            Map<Long, PoiChunk> cols = new HashMap<>();
            int span = 6 + r.nextInt(5);
            PoiManager poi = randomWorld(r, span, 1 + r.nextInt(round < 30 ? 6 : 40), cols);
            List<int[]> centers = centersOf(cols);
            for (int q = 0; q < 60; q++) {
                int[] sp = {r.nextInt(span * 2 + 5) - span - 2, r.nextInt(30) - 7, r.nextInt(span * 2 + 5) - span - 2};   // 높이 끝·열 밖 포함
                for (int radius = 0; radius <= 3; radius++) {
                    checkAll(poi, centers, sp, radius, "round " + round + " sp " + java.util.Arrays.toString(sp) + " r " + radius);
                    total++;
                    if (poi.closestVillageSection(sp, radius) != sp) villagePicks++;
                }
            }
        }
        assertTrue(villagePicks > total / 20, "마을로 가는 답이 충분히 섞여야 의미 있는 시험이다: " + villagePicks + "/" + total);
    }


    /** 독립 오라클: 운영 코드(sectionsToVillage·isVillageCenter)를 안 쓰고, 열 맵에서 직접 뽑은 마을 중심 목록으로 계산한다. */
    private static List<int[]> centersOf(Map<Long, PoiChunk> cols) {
        List<int[]> out = new ArrayList<>();
        for (var e : cols.entrySet()) {
            int cx = (int) (e.getKey() >> 32), cz = (int) (long) e.getKey();
            PoiChunk c = e.getValue();
            if (!c.hasSections()) continue;
            for (int y = c.minSection(); y <= c.maxSection(); y++) {
                PoiSection sec = c.section(y);
                if (sec != null && sec.anyRecord(PoiTypes.VILLAGE, PoiManager.Occupancy.IS_OCCUPIED)) out.add(new int[] {cx, y, cz});
            }
        }
        return out;
    }

    private static int dist(List<int[]> centers, int x, int y, int z) {
        int best = PoiManager.MAX_VILLAGE_DISTANCE + 1;
        for (int[] c : centers) best = Math.min(best, Math.max(Math.abs(c[0] - x), Math.max(Math.abs(c[1] - y), Math.abs(c[2] - z))));
        return best;
    }

    private static int[] indep(List<int[]> centers, int[] sp, int radius) {
        int cur = dist(centers, sp[0], sp[1], sp[2]);
        int[] best = null;
        int bestD = Integer.MAX_VALUE;
        for (int z = sp[2] - radius; z <= sp[2] + radius; z++)
            for (int y = sp[1] - radius; y <= sp[1] + radius; y++)
                for (int x = sp[0] - radius; x <= sp[0] + radius; x++) {
                    int d = dist(centers, x, y, z);
                    if (d < cur && d < bestD) {
                        bestD = d;
                        best = new int[] {x, y, z};
                    }
                }
        return best == null ? sp : best;
    }

    private static int windowCenters(List<int[]> centers, int[] sp, int radius) {
        int win = radius + PoiManager.MAX_VILLAGE_DISTANCE, n = 0;
        for (int[] c : centers) if (Math.abs(c[0] - sp[0]) <= win && Math.abs(c[1] - sp[1]) <= win && Math.abs(c[2] - sp[2]) <= win) n++;
        return n;
    }

    /** 새 구현·옛 구현·독립 오라클이 모두 같은지, 철골렘 5×5×5 마을 섹션(z → y → x)도 같은지. */
    private static void checkAll(PoiManager poi, List<int[]> centers, int[] sp, int radius, String tag) {
        int[] a = old(poi, sp, radius), b = poi.closestVillageSection(sp, radius), c = indep(centers, sp, radius);
        assertArrayEquals(a, b, tag + " 새 vs 옛");
        assertArrayEquals(c, b, tag + " 새 vs 독립");
        assertEquals(a == sp, b == sp, tag + " sp 참조");
        if (radius != 0) return;   // 골렘 5×5×5 는 위치마다 한 번만(옛 sectionsToVillage 125번이 느리다)
        for (int z = sp[2] - 2; z <= sp[2] + 2; z++)
            for (int y = sp[1] - 2; y <= sp[1] + 2; y++)
                for (int x = sp[0] - 2; x <= sp[0] + 2; x++) {
                    assertEquals(poi.sectionsToVillage(x, y, z) == 0, poi.isVillageCenterSection(x, y, z), tag + " 골렘 옛 " + x + "," + y + "," + z);
                    assertEquals(dist(centers, x, y, z) == 0, poi.isVillageCenterSection(x, y, z), tag + " 골렘 독립 " + x + "," + y + "," + z);
                }
    }

    @Test
    void denseVillagesGrowCenterBufferAndMatchIndependentOracle() {
        Random r = new Random(0xD3E5);
        int maxWindow = 0;
        for (int round = 0; round < 12; round++) {
            Map<Long, PoiChunk> cols = new HashMap<>();
            PoiManager poi = randomWorld(r, 5, 500 + r.nextInt(300), cols);   // 창 안 중심이 16개(초기 버퍼)를 훨씬 넘는다
            List<int[]> centers = centersOf(cols);
            for (int q = 0; q < 8; q++) {
                int[] sp = {r.nextInt(9) - 4, r.nextInt(20) - 6, r.nextInt(9) - 4};
                for (int radius = 0; radius <= 3; radius++) {
                    maxWindow = Math.max(maxWindow, windowCenters(centers, sp, radius));
                    checkAll(poi, centers, sp, radius, "dense round " + round);
                }
            }
        }
        assertTrue(maxWindow > 16 * 4, "버퍼 늘리기 경로(중심 16개 초과)를 충분히 타야 한다: 창 안 최대 " + maxWindow);
    }

    @Test
    void noVillageReturnsSameInstance() {
        Map<Long, PoiChunk> cols = new HashMap<>();
        PoiManager poi = randomWorld(new Random(1), 3, 0, cols);
        int[] sp = {0, 0, 0};
        assertSame(sp, poi.closestVillageSection(sp, 2));
    }
}
