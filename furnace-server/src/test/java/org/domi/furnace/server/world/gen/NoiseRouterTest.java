// NoiseRouter + NoiseSettings 통합 테스트: overworld JSON 파싱 및 final_density 유한값 검증
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.world.gen.df.DensityFunction;

class NoiseRouterTest {

    @Test
    void settingsAndRouter() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings s = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        assertEquals(-64, s.minY());
        assertEquals(384, s.height());
        assertEquals(63, s.seaLevel());
        assertEquals(4, s.cellWidth());
        assertEquals(8, s.cellHeight());
        NoiseRouter r = NoiseRouter.build(data, s, 0L);
        double v = r.finalDensity().compute(new DensityFunction.SinglePointContext(0, 0, 0));
        assertTrue(Double.isFinite(v), "final_density finite, got " + v);
    }

    @Test
    void router_has_climate_dfs() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter r = NoiseRouter.build(data, settings, 0L);

        assertNotNull(r.temperature());
        assertNotNull(r.humidity());
        assertNotNull(r.continents());
        assertNotNull(r.erosion());
        assertNotNull(r.depth());
        assertNotNull(r.ridges());

        DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(0, 64, 0);
        assertTrue(Double.isFinite(r.temperature().compute(ctx)), "temperature must be finite");
        assertTrue(Double.isFinite(r.humidity().compute(ctx)), "humidity must be finite");
        assertTrue(Double.isFinite(r.continents().compute(ctx)), "continents must be finite");
        assertTrue(Double.isFinite(r.erosion().compute(ctx)), "erosion must be finite");
        assertTrue(Double.isFinite(r.depth().compute(ctx)), "depth must be finite");
        assertTrue(Double.isFinite(r.ridges().compute(ctx)), "ridges must be finite");
    }
}
