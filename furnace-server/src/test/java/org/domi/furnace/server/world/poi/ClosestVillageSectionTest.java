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
        for (int round = 0; round < 120; round++) {
            Map<Long, PoiChunk> cols = new HashMap<>();
            int span = 6 + r.nextInt(5);
            PoiManager poi = randomWorld(r, span, 1 + r.nextInt(round < 60 ? 6 : 40), cols);
            for (int q = 0; q < 60; q++) {
                int[] sp = {r.nextInt(span * 2 + 5) - span - 2, r.nextInt(30) - 7, r.nextInt(span * 2 + 5) - span - 2};   // 높이 끝·열 밖 포함
                for (int radius = 0; radius <= 3; radius++) {
                    int[] a = old(poi, sp, radius), b = poi.closestVillageSection(sp, radius);
                    assertArrayEquals(a, b, "round " + round + " sp " + java.util.Arrays.toString(sp) + " r " + radius);
                    assertEquals(a == sp, b == sp, "sp 자신을 돌려주는 모양(MoveBackToVillageGoal 이 참조 비교)");
                    total++;
                    if (a != sp) villagePicks++;
                }
                assertEquals(poi.sectionsToVillage(sp[0], sp[1], sp[2]) == 0, poi.isVillageCenterSection(sp[0], sp[1], sp[2]));
            }
        }
        assertTrue(villagePicks > total / 20, "마을로 가는 답이 충분히 섞여야 의미 있는 시험이다: " + villagePicks + "/" + total);
    }

    @Test
    void noVillageReturnsSameInstance() {
        Map<Long, PoiChunk> cols = new HashMap<>();
        PoiManager poi = randomWorld(new Random(1), 3, 0, cols);
        int[] sp = {0, 0, 0};
        assertSame(sp, poi.closestVillageSection(sp, 2));
    }
}
