// 노이즈/캐시/마커 DF 노드 단위 검증 테스트 (Task 9)
package org.domi.furnace.server.world.gen.df;

import org.domi.furnace.server.world.gen.noise.BlendedNoise;
import org.domi.furnace.server.world.gen.noise.NormalNoise;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DensityFunctionNoiseNodeTest {

    static DensityFunction.FunctionContext at(int x, int y, int z) {
        return new DensityFunction.SinglePointContext(x, y, z);
    }

    // ── noise 노드: NormalNoise를 직접 감싼 결과와 동일해야 한다 ──────────────

    @Test
    void noiseNodeDelegatesToNormalNoise() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(0L), -7, 1, 1, 1, 1);
        DensityFunction noiseNode = DensityFunctions.noise(nn, 1.0, 1.0);

        for (int[] c : new int[][]{{0,0,0},{1,2,3},{-5,64,10},{100,-20,300}}) {
            double expected = nn.getValue(c[0], c[1], c[2]);
            double actual   = noiseNode.compute(at(c[0], c[1], c[2]));
            assertEquals(expected, actual, 0.0,
                "noise node mismatch at (" + c[0] + "," + c[1] + "," + c[2] + ")");
        }
    }

    @Test
    void noiseNodeAppliesScale() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(42L), -7, 1, 1, 1, 1);
        double xz = 0.5, y = 0.25;
        DensityFunction noiseNode = DensityFunctions.noise(nn, xz, y);

        int x = 3, by = 10, z = -7;
        double expected = nn.getValue(x * xz, by * y, z * xz);
        assertEquals(expected, noiseNode.compute(at(x, by, z)), 0.0);
    }

    // ── old_blended_noise 노드 ────────────────────────────────────────────────

    @Test
    void oldBlendedNoiseNodeMatchesCalculate() {
        BlendedNoise bn = new BlendedNoise(new XoroshiroRandomSource(0L), 0.25, 0.125, 80, 160, 8);
        DensityFunction node = DensityFunctions.oldBlendedNoise(bn);

        for (int[] c : new int[][]{{0,64,0},{4,70,8},{-12,40,20}}) {
            double expected = bn.calculate(c[0], c[1], c[2]);
            double actual   = node.compute(at(c[0], c[1], c[2]));
            assertEquals(expected, actual, 0.0,
                "old_blended_noise mismatch at (" + c[0] + "," + c[1] + "," + c[2] + ")");
        }
    }

    // ── Marker / cache 노드: 패스스루 ────────────────────────────────────────

    @Test
    void interpolatedIsPassThrough() {
        DensityFunction f = DensityFunctions.interpolated(DensityFunctions.constant(7.0));
        assertEquals(7.0, f.compute(at(1, 2, 3)), 0.0);
    }

    @Test
    void flatCacheIsPassThrough() {
        DensityFunction f = DensityFunctions.flatCache(DensityFunctions.constant(3.5));
        assertEquals(3.5, f.compute(at(0, 0, 0)), 0.0);
    }

    @Test
    void cache2dIsPassThrough() {
        DensityFunction f = DensityFunctions.cache2d(DensityFunctions.constant(-1.0));
        assertEquals(-1.0, f.compute(at(5, 10, 15)), 0.0);
    }

    @Test
    void cacheOnceIsPassThrough() {
        DensityFunction f = DensityFunctions.cacheOnce(DensityFunctions.constant(2.5));
        assertEquals(2.5, f.compute(at(0, 64, 0)), 0.0);
    }

    @Test
    void cacheAllInCellIsPassThrough() {
        DensityFunction f = DensityFunctions.cacheAllInCell(DensityFunctions.constant(9.9));
        assertEquals(9.9, f.compute(at(-1, -1, -1)), 0.0);
    }

    @Test
    void markerWrappedAccessorWorks() {
        DensityFunction inner = DensityFunctions.constant(42.0);
        DensityFunctions.Marker m = (DensityFunctions.Marker) DensityFunctions.interpolated(inner);
        assertEquals(DensityFunctions.Marker.Type.Interpolated, m.type());
        assertEquals(inner, m.wrapped());
    }

    @Test
    void markerMinMaxDelegateToWrapped() {
        DensityFunction inner = DensityFunctions.constant(5.0);
        DensityFunction f = DensityFunctions.cacheOnce(inner);
        assertEquals(5.0, f.minValue(), 0.0);
        assertEquals(5.0, f.maxValue(), 0.0);
    }

    // ── blend 노드 ────────────────────────────────────────────────────────────

    @Test
    void blendAlphaIsConstantOne() {
        assertEquals(1.0, DensityFunctions.blendAlpha().compute(at(0, 0, 0)), 0.0);
        assertEquals(1.0, DensityFunctions.blendAlpha().minValue(), 0.0);
        assertEquals(1.0, DensityFunctions.blendAlpha().maxValue(), 0.0);
    }

    @Test
    void blendOffsetIsConstantZero() {
        assertEquals(0.0, DensityFunctions.blendOffset().compute(at(0, 0, 0)), 0.0);
        assertEquals(0.0, DensityFunctions.blendOffset().minValue(), 0.0);
        assertEquals(0.0, DensityFunctions.blendOffset().maxValue(), 0.0);
    }

    @Test
    void blendDensityIsPassThrough() {
        DensityFunction f = DensityFunctions.blendDensity(DensityFunctions.constant(5.0));
        assertEquals(5.0, f.compute(at(0, 0, 0)), 0.0);
    }

    // ── shiftedNoise: 0 오프셋이면 plain noise scaling과 동일 ────────────────

    @Test
    void shiftedNoiseWithZeroShiftMatchesScaledNoise() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(123L), -7, 1, 1, 1, 1);
        double xz = 2.0, y = 0.5;
        DensityFunction zero = DensityFunctions.constant(0.0);
        DensityFunction f = DensityFunctions.shiftedNoise(zero, zero, zero, xz, y, nn);

        for (int[] c : new int[][]{{1,5,3},{10,-3,7}}) {
            double expected = nn.getValue(c[0] * xz, c[1] * y, c[2] * xz);
            double actual   = f.compute(at(c[0], c[1], c[2]));
            assertEquals(expected, actual, 0.0,
                "shiftedNoise zero-shift mismatch at (" + c[0] + "," + c[1] + "," + c[2] + ")");
        }
    }

    // ── ShiftNoise 노드: 공식 검증 ─────────────────────────────────────────────

    @Test
    void shiftAComputesCorrectly() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(77L), -7, 1, 1, 1, 1);
        // ShiftA: compute(x, 0, z)  →  nn.getValue(x*0.25, 0*0.25, z*0.25) * 4.0
        DensityFunction f = DensityFunctions.shiftA(nn);
        int x = 4, z = 8;
        double expected = nn.getValue(x * 0.25, 0.0, z * 0.25) * 4.0;
        assertEquals(expected, f.compute(at(x, 99, z)), 0.0);
    }

    @Test
    void shiftBComputesCorrectly() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(88L), -7, 1, 1, 1, 1);
        // ShiftB: compute(z, x, 0)  →  nn.getValue(z*0.25, x*0.25, 0*0.25) * 4.0
        DensityFunction f = DensityFunctions.shiftB(nn);
        int x = 3, z = 6;
        double expected = nn.getValue(z * 0.25, x * 0.25, 0.0) * 4.0;
        assertEquals(expected, f.compute(at(x, 99, z)), 0.0);
    }

    @Test
    void shiftComputesCorrectly() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(55L), -7, 1, 1, 1, 1);
        // Shift: compute(x, y, z)  →  nn.getValue(x*0.25, y*0.25, z*0.25) * 4.0
        DensityFunction f = DensityFunctions.shift(nn);
        int x = 2, y = 4, z = 6;
        double expected = nn.getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        assertEquals(expected, f.compute(at(x, y, z)), 0.0);
    }

    // ── WeirdScaledSampler ────────────────────────────────────────────────────

    @Test
    void weirdScaledSamplerType1() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(999L), -7, 1, 1, 1, 1);
        // Use a constant input that maps to rarity=2.0 for TYPE1 (value >= 0.5)
        // type_1 getSpaghettiRarity3D: value >= 0.5 → 2.0
        double inputVal = 0.6;
        DensityFunction input = DensityFunctions.constant(inputVal);
        DensityFunction f = DensityFunctions.weirdScaledSampler(input, nn,
                DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1);

        int x = 4, y = 8, z = 12;
        double rarity = 2.0; // getSpaghettiRarity3D(0.6) = 2.0
        double expected = rarity * Math.abs(nn.getValue(x / rarity, y / rarity, z / rarity));
        assertEquals(expected, f.compute(at(x, y, z)), 0.0);
    }

    @Test
    void weirdScaledSamplerType2() {
        NormalNoise nn = NormalNoise.create(new XoroshiroRandomSource(888L), -7, 1, 1, 1, 1);
        // type_2 getSphaghettiRarity2D: value >= 0.75 → 3.0
        double inputVal = 0.8;
        DensityFunction input = DensityFunctions.constant(inputVal);
        DensityFunction f = DensityFunctions.weirdScaledSampler(input, nn,
                DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2);

        int x = 2, y = 4, z = 6;
        double rarity = 3.0; // getSphaghettiRarity2D(0.8) = 3.0
        double expected = rarity * Math.abs(nn.getValue(x / rarity, y / rarity, z / rarity));
        assertEquals(expected, f.compute(at(x, y, z)), 0.0);
    }
}
