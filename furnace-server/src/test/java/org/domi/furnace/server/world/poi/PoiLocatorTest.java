// /locate poi: findClosestWithType 의 고르기(사각 → 구 → 먼저 나온 최소).
package org.domi.furnace.server.world.poi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.Test;

class PoiLocatorTest {

    @Test
    void squareOrder() {
        var sq = PoiLocator.square(new Pos(0, 0, 0), 256);
        assertEquals(35 * 35, sq.size());
        assertEquals(new ChunkPos(-17, -17), sq.get(0));
        assertEquals(new ChunkPos(-16, -17), sq.get(1));   // x 가 안쪽
        assertEquals(new ChunkPos(17, 17), sq.get(sq.size() - 1));
    }

    @Test
    void closest() {
        Pos o = new Pos(0, 64, 0);
        var order = PoiLocator.square(o, 256);
        var a = new PoiLocator.Entry(PoiTypes.HOME, new Pos(10, 64, 0));
        var b = new PoiLocator.Entry(PoiTypes.MEETING, new Pos(-10, 64, 0));   // 같은 거리, 앞 청크
        var far = new PoiLocator.Entry(PoiTypes.HOME, new Pos(200, 64, 200));   // 사각 안, 구 밖
        var out = new PoiLocator.Entry(PoiTypes.HOME, new Pos(0, 64, 257));     // 사각 밖
        var m = Map.of(new ChunkPos(0, 0), List.of(a), new ChunkPos(-1, 0), List.of(b),
            new ChunkPos(12, 12), List.of(far), new ChunkPos(0, 16), List.of(out));
        assertSame(b, PoiLocator.closest(order, m, o, 256));
        assertNull(PoiLocator.closest(order, Map.of(new ChunkPos(12, 12), List.of(far), new ChunkPos(0, 16), List.of(out)), o, 256));
    }
}
