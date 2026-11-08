// A07-03 회귀(단위): 틱 범위 밖 청크의 예약 틱은 꺼내지 않고 남겨 두었다가(바닐라 LevelTicks), 범위에 들면 원래 순서대로 돈다.
// 블록 이벤트는 처리하지 않고 다음 틱으로 미룬다(바닐라 ServerLevel.runBlockEvents 의 blockEventsToReschedule).
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DeferredBlockTicksTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final LongPredicate ONLY_CHUNK_0_0 = k -> k == ChunkPredicateMemo.chunkKey(0, 0);

    @Test
    void outOfRangeTicksStayQueuedAndRunInOrderLater() {
        BlockTicks ticks = new BlockTicks();
        Pos near = new Pos(1, 0, 1), far1 = new Pos(33, 0, 1), far2 = new Pos(34, 0, 1);   // far = 청크 (2,0)
        ticks.schedule(far1, 7, 5, 0, 0);
        ticks.schedule(near, 7, 5, 0, 1);
        ticks.schedule(far2, 7, 5, -1, 2);   // 우선순위가 더 높다
        List<Pos> ran = new ArrayList<>();
        ticks.tick(5, 65536, ONLY_CHUNK_0_0, (p, t) -> ran.add(p));
        assertEquals(List.of(near), ran);
        assertEquals(2, ticks.size(), "범위 밖 예약은 남아 있다");
        assertTrue(ticks.hasScheduledTick(far1, 7), "남아 있는 동안 같은 예약은 다시 걸리지 않는다");
        ticks.schedule(far1, 7, 9, 0, 3);
        assertEquals(2, ticks.size());
        assertEquals(List.of(far2, far1), sorted(ticks.chunkTicks(2, 0)), "저장할 때도 남은 예약이 나온다");

        ran.clear();
        ticks.tick(6, 65536, ONLY_CHUNK_0_0, (p, t) -> ran.add(p));
        assertTrue(ran.isEmpty());
        ticks.tick(7, 65536, k -> true, (p, t) -> ran.add(p));   // 범위에 들었다 — 원래 (시각, 우선순위, 순번) 순서로
        assertEquals(List.of(far2, far1), ran);
        assertEquals(0, ticks.size());
    }

    @Test
    void outOfRangeTicksDoNotCountTowardTheLimit() {
        BlockTicks ticks = new BlockTicks();
        for (int i = 0; i < 5; i++) ticks.schedule(new Pos(40 + i, 0, 0), 7, 1, 0, i);   // 청크 (2,0)
        ticks.schedule(new Pos(1, 0, 1), 7, 1, 0, 9);
        List<Pos> ran = new ArrayList<>();
        ticks.tick(1, 1, ONLY_CHUNK_0_0, (p, t) -> ran.add(p));
        assertEquals(List.of(new Pos(1, 0, 1)), ran, "바닐라는 범위 안 청크에서만 한도(maxAllowed)까지 꺼낸다");
    }

    @Test
    void removingAChunkTakesItsDeferredTicks() {
        BlockTicks ticks = new BlockTicks();
        Pos far = new Pos(33, 0, 1);
        ticks.schedule(far, 7, 1, 0, 0);
        ticks.tick(1, 65536, ONLY_CHUNK_0_0, (p, t) -> fail("범위 밖"));
        assertEquals(1, ticks.removeChunk(2, 0).size());
        assertEquals(0, ticks.size());
        assertFalse(ticks.hasScheduledTick(far, 7));
    }

    @Test
    void deferredTicksMoveWithRegionMergeAndSplit() {
        BlockTicks a = new BlockTicks(), b = new BlockTicks();
        Pos far = new Pos(33, 0, 1);
        a.schedule(far, 7, 1, 0, 0);
        a.tick(1, 65536, ONLY_CHUNK_0_0, (p, t) -> fail("범위 밖"));
        a.moveTo(b, pos -> true, 0);
        assertEquals(0, a.size());
        assertTrue(b.hasScheduledTick(far, 7));
        List<Pos> ran = new ArrayList<>();
        b.tick(2, 65536, k -> true, (p, t) -> ran.add(p));
        assertEquals(List.of(far), ran);
    }

    @Test
    void blockEventsOutOfRangeWaitForTheChunk() {
        var level = new BlockPlacementTest.TestLevel();
        int piston = B.prop("facing").with(B.defaultState(B.id("piston")), "east");
        int rb = B.defaultState(B.id("redstone_block"));
        Pos far = new Pos(36, -60, 4);   // 청크 (2,0)
        level.setBlock(far, piston, 3);
        level.setBlock(far.west(), rb, 3);   // 피스톤이 신호를 받아 블록 이벤트(늘리기)를 건다
        for (int t = 0; t < 3; t++) {
            level.runScheduledTicks(ONLY_CHUNK_0_0);
            level.runBlockEvents(ONLY_CHUNK_0_0);
            level.tickBlockEntities(ONLY_CHUNK_0_0);
        }
        assertEquals("minecraft:piston[extended=false,facing=east]", Blocks.get().describe(level.getBlockState(far)), "범위 밖: 이벤트가 돌지 않는다");
        for (int t = 0; t < 4; t++) {
            level.runScheduledTicks(k -> true);
            level.runBlockEvents(k -> true);
            level.tickBlockEntities(k -> true);
        }
        assertEquals("minecraft:piston[extended=true,facing=east]", Blocks.get().describe(level.getBlockState(far)), "범위에 들면 남아 있던 이벤트가 돈다");
        assertEquals("minecraft:piston_head[facing=east,short=false,type=normal]", Blocks.get().describe(level.getBlockState(far.east())));
    }

    private static List<Pos> sorted(List<BlockTicks.Scheduled> l) {
        List<Pos> out = new ArrayList<>();
        for (var s : l) out.add(Pos.of(s.pos()));
        return out;
    }
}
