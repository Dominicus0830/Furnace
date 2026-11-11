// 표면 규칙 바이옴 조회가 바닐라 모델과 블록 단위로 같은지.
// 바닐라 모델 = BiomeManager.getBiome(지터 식은 여기서 따로 옮김) + 꼭짓점 바이옴은 WorldGenRegion.getNoiseBiome:
//   그 quart 가 속한 청크(자기·이웃)의 저장 바이옴(Furnace = sampleChunk 결과), quart y 는 차원 높이 범위로 자른다(ChunkAccess.getNoiseBiome).
// 꼭짓점 표(생략·지터 메모)와 블록마다 지터 경로, 이웃 배열 캐시가 찬 경우·빈 경우(quart 검색 + 동률이면 이웃 청크 계산)를 모두 대조한다.
package org.domi.furnace.server.world.gen.surface;

import static org.junit.jupiter.api.Assertions.*;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class SurfaceZoomTableTest {

    static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    /** 원점 근처, 흩어진 곳, 음수, 먼 곳, 월드 경계(청크 ±1874999) 바로 안·바깥, 오버월드 시드 3 의 R-트리 동률 이웃(5,1)·(6,2). */
    static final int[][] CHUNKS = {
        {0, 0}, {-1, -1}, {3, -7}, {-40, 25}, {117, 88}, {-260, -301}, {1500, -900}, {-12000, 7000},
        {1874999, 1874999}, {-1875000, -1875000}, {1875001, -1874999}, {5, 1}, {6, 2},
    };

    record Cfg(String name, long seed, int dimMinY, int dimHeight, int genMinY, int genHeight, java.util.function.Supplier<NoiseWorldSource> src) {}

    static List<Cfg> configs() {
        return List.of(
            new Cfg("nether0", 0L, 0, 256, 0, 128, () -> NoiseWorldSource.nether(0L, BLOCKS, REGISTRIES)),
            new Cfg("nether12345", 12345L, 0, 256, 0, 128, () -> NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES)),
            new Cfg("overworld3", 3L, -64, 384, -64, 384, () -> NoiseWorldSource.overworld(3L, BLOCKS, REGISTRIES)),
            new Cfg("overworld42", 42L, -64, 384, -64, 384, () -> NoiseWorldSource.overworld(42L, BLOCKS, REGISTRIES)),
            new Cfg("large_biomes-1", -1L, -64, 384, -64, 384,
                () -> NoiseWorldSource.overworld(-1L, BLOCKS, REGISTRIES, "minecraft:large_biomes")),
            new Cfg("amplified7", 7L, -64, 384, -64, 384,
                () -> NoiseWorldSource.overworld(7L, BLOCKS, REGISTRIES, "minecraft:amplified")),
            new Cfg("end0", 0L, 0, 256, 0, 128, () -> NoiseWorldSource.end(0L, BLOCKS, REGISTRIES)));
    }

    // ---- 바닐라 BiomeManager(ddy) — 구현과 따로 옮긴 식 ----
    static long obf(long seed) throws Exception {
        byte[] in = new byte[8];
        for (int i = 0; i < 8; i++) in[i] = (byte) (seed >>> (8 * i));
        byte[] h = MessageDigest.getInstance("SHA-256").digest(in);
        long r = 0;
        for (int i = 7; i >= 0; i--) r = (r << 8) | (h[i] & 0xFF);
        return r;
    }
    static long lcg(long l, long m) { l *= l * 6364136223846793005L + 1442695040888963407L; return l + m; }
    static double fiddle(long l) { double d = (double) Math.floorMod(l >> 24, 1024) / 1024.0; return (d - 0.5) * 0.9; }
    static double sq(double v) { return v * v; }
    static double dist(long seed, int x, int y, int z, double dx, double dy, double dz) {
        long m = lcg(seed, x); m = lcg(m, y); m = lcg(m, z); m = lcg(m, x); m = lcg(m, y); m = lcg(m, z);
        double g = fiddle(m); m = lcg(m, seed); double h = fiddle(m); m = lcg(m, seed); double n = fiddle(m);
        return sq(dz + n) + sq(dy + h) + sq(dx + g);
    }
    interface Src { int get(int qx, int qy, int qz); }
    static int vanillaGetBiome(long zoomSeed, int bx, int by, int bz, Src src) {
        int i = bx - 2, j = by - 2, k = bz - 2;
        int l = i >> 2, m = j >> 2, n = k >> 2;
        double d = (i & 3) / 4.0, e = (j & 3) / 4.0, f = (k & 3) / 4.0;
        int o = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int p = 0; p < 8; p++) {
            boolean b1 = (p & 4) == 0, b2 = (p & 2) == 0, b3 = (p & 1) == 0;
            double s = dist(zoomSeed, b1 ? l : l + 1, b2 ? m : m + 1, b3 ? n : n + 1, b1 ? d : d - 1.0, b2 ? e : e - 1.0, b3 ? f : f - 1.0);
            if (best > s) { o = p; best = s; }
        }
        return src.get((o & 4) == 0 ? l : l + 1, (o & 2) == 0 ? m : m + 1, (o & 1) == 0 ? n : n + 1);
    }

    @Test
    void blockBiomesMatchVanillaModel() throws Exception {
        int mixedChunks = 0, checked = 0;
        for (Cfg c : configs()) {
            long zs = obf(c.seed);
            NoiseWorldSource ref = c.src.get();
            Map<Long, int[]> arrays = new HashMap<>();
            int dq0 = c.dimMinY >> 2, dqMax = dq0 + (c.dimHeight >> 2) - 1;
            Src vanilla = (qx, qy, qz) -> {
                int ncx = qx >> 2, ncz = qz >> 2;
                int[] a = arrays.computeIfAbsent(((long) ncx << 32) ^ (ncz & 0xFFFFFFFFL), k -> ref.sampleBiomes(ncx, ncz));
                int qyc = Math.max(dq0, Math.min(dqMax, qy));
                return a[((qyc - dq0) * 4 + (qz & 3)) * 4 + (qx & 3)];
            };
            // 위치들이 서로 이웃하지 않으므로 생성기 하나에서 이웃 캐시는 "빔" 상태로 남는다(동률 계산분 빼고)
            NoiseWorldSource cold = c.src.get(), cold2 = c.src.get(), warm = c.src.get();
            for (int[] ch : CHUNKS) {
                for (int d = 0; d < 2; d++) {
                    int cx = ch[0] + d * 5, cz = ch[1] - d * 3;
                    int[] expect = new int[c.genHeight * 256];
                    int i = 0;
                    Set<Integer> distinct = new HashSet<>();
                    for (int y = c.genMinY; y < c.genMinY + c.genHeight; y++)
                        for (int z = 0; z < 16; z++)
                            for (int x = 0; x < 16; x++) {
                                expect[i] = vanillaGetBiome(zs, cx * 16 + x, y, cz * 16 + z, vanilla);
                                distinct.add(expect[i++]);
                            }
                    // 이웃 캐시가 빈 생성기(quart 검색·동률이면 이웃 청크 계산) — 표·블록마다 둘 다
                    int[] own = cold.generateWithBiomes(cx, cz).biomeIds();
                    assertArrayEquals(expect, cold.surfaceSystem().blockBiomesForTest(own, cx, cz, true), c.name + " 차가움·표 " + cx + "," + cz);
                    assertArrayEquals(expect, cold2.surfaceSystem().blockBiomesForTest(cold2.generateWithBiomes(cx, cz).biomeIds(), cx, cz, false),
                        c.name + " 차가움·블록마다 " + cx + "," + cz);
                    // 이웃 8개를 먼저 만든 생성기(이웃 배열 캐시)
                    for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) if (dx != 0 || dz != 0) warm.generateWithBiomes(cx + dx, cz + dz);
                    assertArrayEquals(expect, warm.surfaceSystem().blockBiomesForTest(warm.generateWithBiomes(cx, cz).biomeIds(), cx, cz, true),
                        c.name + " 따뜻함·표 " + cx + "," + cz);
                    if (distinct.size() > 1) mixedChunks++;
                    checked++;
                }
            }
        }
        assertEquals(configs().size() * CHUNKS.length * 2, checked);
        assertTrue(mixedChunks >= 20, "바이옴 경계 청크가 너무 적다: " + mixedChunks);
    }

    /** 오버월드 시드 3 의 quart (24,-7,7): 기후 거리 동률이라 quart 단독 검색(lush_caves)과 청크 저장 바이옴(river, 힌트)이 다르다. */
    @Test
    void tieQuartUsesNeighbourChunkArray() {
        NoiseWorldSource src = NoiseWorldSource.overworld(3L, BLOCKS, REGISTRIES);
        int[] stored = src.sampleBiomes(6, 1);
        int idx = ((-7 + 16) * 4 + 3) * 4;
        assertEquals("minecraft:river", src.biomeName(stored[idx]));
        assertEquals("minecraft:lush_caves", src.biomeName(src.noiseBiomeIdAtQuart(24, -7, 7)), "동률 예시가 사라졌다 — 다른 예시로 바꿀 것");
    }

    @Test
    void generatedChunksMatchWithTableOff() throws Exception {
        List<NoiseWorldSource> fast = new ArrayList<>(), slow = new ArrayList<>();
        fast.add(NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES));
        fast.add(NoiseWorldSource.overworld(3L, BLOCKS, REGISTRIES));
        System.setProperty("furnace.surface.zoomTable", "false");
        try {
            slow.add(NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES));
            slow.add(NoiseWorldSource.overworld(3L, BLOCKS, REGISTRIES));
        } finally {
            System.clearProperty("furnace.surface.zoomTable");
        }
        // 여러 스레드가 같은 생성기로 동시에 만든 청크(이웃 배열 캐시가 순서에 따라 다르게 찬다)도 같아야 한다
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int k = 0; k < fast.size(); k++) {
                NoiseWorldSource f = fast.get(k), s = slow.get(k);
                List<Future<ChunkColumn>> futures = new ArrayList<>();
                List<int[]> where = new ArrayList<>();
                for (int[] c : CHUNKS) {
                    for (int d = 0; d < 2; d++) {
                        int cx = c[0] + d, cz = c[1];
                        where.add(new int[]{cx, cz});
                        futures.add(pool.submit(() -> f.generateWorldChunk(cx, cz).col()));
                    }
                }
                for (int i = where.size() - 1; i >= 0; i--) {
                    int cx = where.get(i)[0], cz = where.get(i)[1];
                    ChunkColumn a = futures.get(i).get(), b = s.generateWorldChunk(cx, cz).col();
                    for (int y = a.minY(); y <= a.maxY(); y++)
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                if (a.getBlock(x, y, z) != b.getBlock(x, y, z))
                                    fail("청크 " + cx + "," + cz + " (" + x + "," + y + "," + z + ") 가 다르다");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
