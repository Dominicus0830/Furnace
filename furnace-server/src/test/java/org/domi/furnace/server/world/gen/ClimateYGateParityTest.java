// W-09: 기후 Y-불변 게이트(j=0 1회 평가)가 게이트 없는 전 칸 평가와 비트 단위로 같은지 무작위 청크·시드로 대조.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ClimateYGateParityTest {

    private static void assertSameBits(double[][][][] a, double[][][][] b, String where) {
        for (int d = 0; d < a.length; d++)
            for (int i = 0; i < a[d].length; i++)
                for (int j = 0; j < a[d][i].length; j++)
                    for (int k = 0; k < a[d][i][j].length; k++)
                        assertEquals(Double.doubleToRawLongBits(b[d][i][j][k]), Double.doubleToRawLongBits(a[d][i][j][k]),
                                where + " d=" + d + " i=" + i + " j=" + j + " k=" + k);
    }

    @Test
    void gatedCacheMatchesUngatedBits() throws Exception {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        Random r = new Random(0xC109);
        for (String dim : new String[]{"minecraft:overworld", "minecraft:nether", "minecraft:end"}) {
            NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings(dim));
            for (long seed : new long[]{0L, 12345L, r.nextLong(), r.nextLong()}) {
                NoiseRouter router = NoiseRouter.build(data, settings, seed);
                for (int n = 0; n < 12; n++) {
                    int cx = r.nextInt(20001) - 10000, cz = r.nextInt(20001) - 10000;
                    NoiseChunk chunk = new NoiseChunk(cx, cz, router, settings);
                    if (dim.equals("minecraft:overworld")) {
                        // 오버월드: depth(4)만 Y 의존, 나머지 5개는 게이트가 걸려야 이득이 있다
                        for (int d = 0; d < 6; d++) {
                            if (d == 4) assertFalse(chunk.climateYInvariantForTest(d));
                            else assertTrue(chunk.climateYInvariantForTest(d), "d=" + d);
                        }
                    }
                    assertSameBits(chunk.climateCacheForTest(), chunk.climateCacheUngatedForTest(),
                            dim + " seed=" + seed + " chunk=" + cx + "," + cz);
                }
            }
        }
    }

    static volatile double sink;

    // -Dfurnace.bench=true 일 때만. 기후 캐시 구성 시간: 게이트 전(전 칸) / 후. 교대 5라운드, 워밍업 300 + 측정 300 청크.
    @Test
    void bench() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("furnace.bench"));
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, 12345L);
        NoiseChunk[] chunks = new NoiseChunk[300];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new NoiseChunk(i * 7, -i * 3, router, settings);
        for (int round = 0; round < 5; round++) {
            for (int g = 0; g < 2; g++) {
                boolean ungated = g == 0;
                for (NoiseChunk c : chunks) sink += (ungated ? c.climateCacheUngatedForTest() : c.rebuildClimateCacheForTest())[0][0][0][0];
                long t = System.nanoTime();
                for (NoiseChunk c : chunks) sink += (ungated ? c.climateCacheUngatedForTest() : c.rebuildClimateCacheForTest())[0][0][0][0];
                double us = (System.nanoTime() - t) / 1000.0 / chunks.length;
                System.out.printf("[BENCH] W-09 round=%d %s %.1f us/청크(기후 캐시)%n", round, ungated ? "전" : "후", us);
            }
        }
    }
}
