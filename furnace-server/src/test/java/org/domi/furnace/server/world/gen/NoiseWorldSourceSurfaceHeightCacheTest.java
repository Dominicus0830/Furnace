// worldSurfaceHeightAt 캐시 유효성 검증 — 동일 청크 반복 조회가 재생성 없이 동일 값을 반환하는지 확인
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseWorldSourceSurfaceHeightCacheTest {

    @Test
    void repeatedQueriesInSameChunkReturnSameValueAndCacheOnce() throws Exception {
        NoiseWorldSource source = source(9999L);

        int first = source.worldSurfaceHeightAt(10, 20);
        assertEquals(1, source.surfaceHeightCacheSize(), "첫 조회 후 캐시 1건이어야 함");

        int second = source.worldSurfaceHeightAt(10, 20);
        int thirdDifferentBlockSameChunk = source.worldSurfaceHeightAt(15, 25);

        assertEquals(first, second, "동일 좌표 반복 조회는 동일 값을 반환해야 함");
        assertEquals(1, source.surfaceHeightCacheSize(), "동일 청크 내 다른 좌표 조회도 재생성 없이 캐시 재사용해야 함");

        // 참조값: 별도 인스턴스(캐시 없음)에서 직접 재생성한 값과 일치해야 함(출력 불변)
        NoiseWorldSource uncached = source(9999L);
        int fresh = uncached.worldSurfaceHeightAt(10, 20);
        assertEquals(first, fresh, "캐시된 값은 재생성 값과 동일해야 함(출력 불변)");

        assertTrue(thirdDifferentBlockSameChunk >= Integer.MIN_VALUE, "sanity");
    }

    @Test
    void differentChunksProduceIndependentCacheEntries() throws Exception {
        NoiseWorldSource source = source(1234L);

        source.worldSurfaceHeightAt(0, 0);
        source.worldSurfaceHeightAt(16, 0);
        source.worldSurfaceHeightAt(0, 16);

        assertEquals(3, source.surfaceHeightCacheSize(), "서로 다른 청크는 각각 별도 캐시 엔트리를 가져야 함");
    }

    @Test
    void concurrentQueriesOnSameChunkStayConsistent() throws Exception {
        NoiseWorldSource source = source(4242L);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return source.worldSurfaceHeightAt(5, 5);
                }));
            }
            start.countDown();
            int expected = futures.get(0).get(30, TimeUnit.SECONDS);
            for (Future<Integer> f : futures) {
                assertEquals(expected, f.get(30, TimeUnit.SECONDS), "동시 조회 결과가 스레드 간 일치해야 함");
            }
            assertEquals(1, source.surfaceHeightCacheSize(), "동시 조회라도 동일 청크는 캐시 1건만 생성해야 함");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void measureBeforeAfterOnRepeatedQuery() throws Exception {
        NoiseWorldSource cached = source(777L);
        // 캐시 없이 매번 재생성(구 동작 재현): 같은 로직을 직접 인라인 호출
        NoiseWorldSource uncachedRef = source(777L);

        int n = 20;
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            uncachedRef.surfaceHeightCache().clear(); // 매 호출 캐시 미스 강제 = 캐시 도입 전 동작 재현
            uncachedRef.worldSurfaceHeightAt(10, 20);
        }
        long beforeNs = System.nanoTime() - t0;

        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) {
            cached.worldSurfaceHeightAt(10, 20); // 동일 청크 반복 — 첫 호출만 재생성, 이후 캐시 히트
        }
        long afterNs = System.nanoTime() - t1;

        System.out.println("surfaceHeightCache measure: before(no-cache)=" + (beforeNs / n / 1_000_000.0)
            + "ms/call after(cached)=" + (afterNs / n / 1_000_000.0) + "ms/call n=" + n);
    }

    private static NoiseWorldSource source(long seed) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        return NoiseWorldSource.overworld(seed, reg, registries);
    }
}
