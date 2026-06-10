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
}
