// 셀 꼭짓점 기둥 공유 캐시: 이웃 청크가 먼저 채운 꼭짓점을 써도 지형 블록이 캐시 없이(처음 계산) 만든 것과 같아야 한다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.domi.furnace.server.world.BlockRegistry;
import org.junit.jupiter.api.Test;

class SliceColumnCacheTest {

    static final BlockRegistry REG = BlockRegistry.load("blocks/1.21.1.txt");
    static final WorldgenData DATA = WorldgenData.load("worldgen/1.21.1");

    private static int[] blocks(NoiseRouter router, NoiseSettings settings, int cx, int cz, BlockRegistry reg) {
        NoiseChunk nc = new NoiseChunk(cx, cz, router, settings, reg);
        Aquifer aq = settings.aquifersEnabled()
            ? new NoiseBasedAquifer(nc, cx, cz, router, settings, router.aquiferRandom(), 1, 2, 3)
            : new DisabledAquifer(settings.seaLevel(), settings.lavaFloorY(), 2, 3);
        return nc.generateBlockIds(1, aq);
    }

    private static void neighborsFirstMatchesFresh(String settingsId, long seed, int cx, int cz) {
        NoiseSettings settings = NoiseSettings.fromJson(settingsId, DATA.noiseSettings(settingsId));
        // 처음 계산: 새 라우터에서 이 청크를 먼저 — 모든 꼭짓점을 직접 계산한다
        NoiseRouter fresh = NoiseRouter.build(DATA, settings, seed);
        int[] expected = blocks(fresh, settings, cx, cz, REG);
        assertTrue(new NoiseChunk(cx, cz, fresh, settings, REG).usesSliceCacheForTest(), settingsId + " 캐시를 써야 한다");

        // 이웃 8개를 먼저 만들어 경계 꼭짓점이 캐시에 있게 한 뒤 같은 청크
        NoiseRouter shared = NoiseRouter.build(DATA, settings, seed);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (dx != 0 || dz != 0) blocks(shared, settings, cx + dx, cz + dz, REG);
        long hits0 = shared.sliceColumns().hits.sum();
        int[] actual = blocks(shared, settings, cx, cz, REG);
        long hits = shared.sliceColumns().hits.sum() - hits0;
        assertTrue(hits > 0, settingsId + " 경계 꼭짓점이 캐시에서 와야 한다");
        assertArrayEquals(expected, actual, settingsId + " seed=" + seed + " " + cx + "," + cz);

        // 같은 라우터에서 광맥 없는(구성이 다른) 청크도 섞여도 서로의 값을 쓰지 않는다
        NoiseRouter freshNoVein = NoiseRouter.build(DATA, settings, seed);
        boolean[] maskExpected = new NoiseChunk(cx + 1, cz, freshNoVein, settings).stoneMask();
        boolean[] maskShared = new NoiseChunk(cx + 1, cz, shared, settings).stoneMask();
        assertArrayEquals(maskExpected, maskShared, settingsId + " 구성이 다른 청크");
    }

    @Test
    void overworldNeighborsShareCorners() {
        neighborsFirstMatchesFresh("minecraft:overworld", 12345L, 3, -7);
        neighborsFirstMatchesFresh("minecraft:overworld", -42L, -100, 250);
    }

    @Test
    void otherSettingsNeighborsShareCorners() {
        neighborsFirstMatchesFresh("minecraft:amplified", 7L, 10, 10);
        neighborsFirstMatchesFresh("minecraft:large_biomes", 7L, -3, 4);
        neighborsFirstMatchesFresh("minecraft:nether", 99L, 2, 2);
        neighborsFirstMatchesFresh("minecraft:end", 99L, 0, 0);
        neighborsFirstMatchesFresh("minecraft:end", 99L, 90, -120);
    }

    /** 보간기 안 cache_once·cache_2d·flat_cache 속에 다른 보간기가 있으면 꼭짓점이 청크 상태에 의존한다 — 캐시를 쓰지 않아야 한다(PR #222 검토 R1). */
    @Test
    void wrappedInterpolatorInsideCacheDisablesSharing() {
        NoiseSettings s = NoiseSettings.fromJson("minecraft:overworld", DATA.noiseSettings("minecraft:overworld"));
        NoiseRouter base = NoiseRouter.build(DATA, s, 1L);
        java.util.List<java.util.function.UnaryOperator<org.domi.furnace.server.world.gen.df.DensityFunction>> wraps = java.util.List.of(
            org.domi.furnace.server.world.gen.df.DensityFunctions::cacheOnce,
            org.domi.furnace.server.world.gen.df.DensityFunctions::cache2d,
            org.domi.furnace.server.world.gen.df.DensityFunctions::flatCache);
        for (var wrap : wraps) {
            var inner = org.domi.furnace.server.world.gen.df.DensityFunctions.interpolated(
                org.domi.furnace.server.world.gen.df.DensityFunctions.yClampedGradient(-64, 320, 1.0, -1.0));
            var fd = org.domi.furnace.server.world.gen.df.DensityFunctions.interpolated(
                org.domi.furnace.server.world.gen.df.DensityFunctions.rangeChoice(
                    org.domi.furnace.server.world.gen.df.DensityFunctions.constant(0.5), 0.0, 1.0, wrap.apply(inner),
                    org.domi.furnace.server.world.gen.df.DensityFunctions.constant(0.0)));
            int[] fresh = new NoiseChunk(0, 0, base.withFinalDensityForTest(fd), s)
                .generateBlockIds(1, new DisabledAquifer(-1000, -1000, 2, 3));
            NoiseRouter shared = base.withFinalDensityForTest(fd);
            assertTrue(!new NoiseChunk(0, 0, shared, s).usesSliceCacheForTest(), "상태 의존 구성은 캐시를 쓰지 않는다");
            new NoiseChunk(-1, 0, shared, s).generateBlockIds(1, new DisabledAquifer(-1000, -1000, 2, 3));
            int[] after = new NoiseChunk(0, 0, shared, s).generateBlockIds(1, new DisabledAquifer(-1000, -1000, 2, 3));
            assertArrayEquals(fresh, after);
        }
    }

    @Test
    void concurrentNeighborsMatchFresh() throws Exception {
        NoiseSettings settings = NoiseSettings.fromJson("minecraft:overworld", DATA.noiseSettings("minecraft:overworld"));
        NoiseRouter fresh = NoiseRouter.build(DATA, settings, 5L);
        int[][] expected = new int[16][];
        for (int i = 0; i < 16; i++) expected[i] = blocks(NoiseRouter.build(DATA, settings, 5L), settings, 50 + i % 4, 60 + i / 4, REG);
        int[][] actual = new int[16][];
        java.util.concurrent.atomic.AtomicInteger next = new java.util.concurrent.atomic.AtomicInteger();
        Thread[] ts = new Thread[4];
        for (int t = 0; t < ts.length; t++) {
            ts[t] = new Thread(() -> {
                for (int i; (i = next.getAndIncrement()) < 16; ) actual[i] = blocks(fresh, settings, 50 + i % 4, 60 + i / 4, REG);
            });
            ts[t].start();
        }
        for (Thread t : ts) t.join();
        for (int i = 0; i < 16; i++) assertArrayEquals(expected[i], actual[i], "청크 " + i);
        assertTrue(fresh.sliceColumns().hits.sum() > 0);
    }
}
