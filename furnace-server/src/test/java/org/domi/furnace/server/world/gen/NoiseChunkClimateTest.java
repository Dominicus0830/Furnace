// NoiseChunk.climateAt() — cell corner 캐시값이 직접 router 평가와 일치하는지 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.junit.jupiter.api.Test;

class NoiseChunkClimateTest {

    @Test
    void climateAtCellCorner_matchesDirectEval() throws Exception {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, 0L);
        NoiseChunk chunk = new NoiseChunk(0, 0, router, settings);

        // cell corner Y → absQY = cy*2 (even). at corners, ty=0 so no interpolation error
        int[] testQX  = {0, 1, 2, 3};
        int[] cellCYs = {0, 1, 10, 47}; // cy=48→absQY=96 은 biome 범위(0..95) 초과
        int[] testQZ  = {0, 1, 3};

        for (int qx : testQX) {
            for (int cy : cellCYs) {
                int absQY = cy * 2; // even → exact cell corner, ty=0
                for (int qz : testQZ) {
                    double[] cached = chunk.climateAt(qx, absQY, qz);

                    int bx = qx * 4;               // chunk 0 → firstNoiseX=0
                    int by = (-8 + cy) * 8;        // cellNoiseMinY=-8, cellHeight=8
                    int bz = qz * 4;
                    DensityFunction.SinglePointContext ctx =
                        new DensityFunction.SinglePointContext(bx, by, bz);

                    assertArrayEquals(
                        new double[]{
                            router.temperature().compute(ctx),
                            router.humidity().compute(ctx),
                            router.continents().compute(ctx),
                            router.erosion().compute(ctx),
                            router.depth().compute(ctx),
                            router.ridges().compute(ctx)
                        },
                        cached,
                        1e-12,
                        "climateAt mismatch at qx=" + qx + " cy=" + cy + " qz=" + qz
                    );
                }
            }
        }
    }
}
