// DensityFunctionParser 통합 테스트: JSON 파싱 + 노이즈 시딩 검증
package org.domi.furnace.server.world.gen.df;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.domi.furnace.server.world.gen.WorldgenData;

class DensityFunctionParserTest {

    @Test
    void parsesAndResolvesRefs() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        DensityFunctionParser p = new DensityFunctionParser(data, 0L);
        DensityFunction f = p.parse("minecraft:overworld/base_3d_noise");
        assertNotNull(f);
        double v = f.compute(new DensityFunction.SinglePointContext(0, 0, 0));
        assertTrue(Double.isFinite(v), "base_3d_noise should be finite, got " + v);
    }

    @Test
    void parsesShiftAndNoiseRefs() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        DensityFunctionParser p = new DensityFunctionParser(data, 0L);
        // overworld/continents: flat_cache > shifted_noise referencing noise params + shift functions
        DensityFunction f = p.parse("minecraft:overworld/continents");
        assertNotNull(f);
        assertTrue(Double.isFinite(f.compute(new DensityFunction.SinglePointContext(10, 20, 30))));
    }

    @Test
    void parsesSplineAndOffset() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        DensityFunctionParser p = new DensityFunctionParser(data, 0L);
        // overworld/offset: flat_cache > cache_2d > spline tree
        DensityFunction f = p.parse("minecraft:overworld/offset");
        assertNotNull(f);
        assertTrue(Double.isFinite(f.compute(new DensityFunction.SinglePointContext(0, 64, 0))));
    }

    @Test
    void cachesReferencedFunctions() {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        DensityFunctionParser p = new DensityFunctionParser(data, 0L);
        // parse twice — should return same instance (cached)
        DensityFunction f1 = p.parse("minecraft:overworld/factor");
        DensityFunction f2 = p.parse("minecraft:overworld/factor");
        assertSame(f1, f2, "Parser should cache parsed functions");
    }
}
