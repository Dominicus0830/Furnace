package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

/** T-072: ApiConvert.block 의 상태 번호별 캐시가 캐시 없는 길과 모든 상태에서 같은 값이고, 같은 번호는 같은 객체이며, 경합해도 깨지지 않는다. */
class ApiConvertBlockCacheTest {

    private static BlockRegistry registry() { return BlockRegistry.load("blocks/1.21.1.txt"); }

    @Test
    void cachedEqualsUncachedForEveryStateAndIsShared() {
        BlockRegistry blocks = registry();
        int n = blocks.totalStateCount();
        assertTrue(n > 26_000);
        for (int i = 0; i < n; i++) {
            BlockData want = ApiConvert.buildBlock(i, blocks);
            BlockData first = ApiConvert.block(i, blocks);
            assertEquals(want, first, "상태 " + i);
            assertEquals(want.toString(), first.toString(), "상태 " + i);
            assertSame(first, ApiConvert.block(i, blocks), "같은 번호는 같은 객체 " + i);
        }
        // 다른 레지스트리는 다른 칸 — 같은 값이지만 객체는 따로(레지스트리와 함께 버려진다)
        BlockRegistry other = registry();
        assertEquals(ApiConvert.block(100, blocks), ApiConvert.block(100, other));
        assertNotSame(ApiConvert.block(100, blocks), ApiConvert.block(100, other));
    }

    @Test
    void outOfRangeStatesAreNotCachedAndBehaveAsBefore() {
        BlockRegistry blocks = registry();
        int n = blocks.totalStateCount();
        for (int id : new int[] {-1, n, n + 1, Integer.MAX_VALUE, Integer.MIN_VALUE}) {
            BlockData want = ApiConvert.buildBlock(id, blocks);
            assertEquals(want, ApiConvert.block(id, blocks), "범위 밖 " + id);
            assertEquals("minecraft:air", ApiConvert.block(id, blocks).type());
        }
    }

    @Test
    void cachedValueIsImmutable() {
        BlockRegistry blocks = registry();
        int stairs = blocks.exactStateId("minecraft:oak_stairs;facing=east,half=top,shape=straight,waterlogged=false");
        assertTrue(stairs >= 0);
        BlockData d = ApiConvert.block(stairs, blocks);
        assertThrows(UnsupportedOperationException.class, () -> d.properties().put("facing", "west"));
        // with() 는 새 값을 주고 공유 값을 건드리지 않는다
        BlockData changed = d.with("facing", "west");
        assertEquals("east", ApiConvert.block(stairs, blocks).properties().get("facing"));
        assertEquals("west", changed.properties().get("facing"));
    }

    @Test
    void concurrentFirstReadsConvergeToEqualValues() throws Exception {
        BlockRegistry blocks = registry();
        int n = blocks.totalStateCount();
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        try {
            var start = new CountDownLatch(1);
            List<Future<BlockData[]>> fs = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int off = t * 3331;
                fs.add(pool.submit(() -> {
                    start.await();
                    BlockData[] seen = new BlockData[n];
                    for (int k = 0; k < n; k++) {
                        int i = (k + off) % n;
                        seen[i] = ApiConvert.block(i, blocks);
                    }
                    return seen;
                }));
            }
            start.countDown();
            List<BlockData[]> all = new ArrayList<>();
            for (var f : fs) all.add(f.get());
            for (int i = 0; i < n; i++) {
                BlockData want = ApiConvert.buildBlock(i, blocks);
                for (BlockData[] seen : all) assertEquals(want, seen[i], "상태 " + i);
                assertSame(ApiConvert.block(i, blocks), ApiConvert.block(i, blocks));
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
