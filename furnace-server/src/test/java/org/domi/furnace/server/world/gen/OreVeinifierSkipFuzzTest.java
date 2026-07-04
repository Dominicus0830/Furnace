// OreVeinifier.canSkipCell 조기스킵 판정 fuzz 검증 — bound 스킵 true ⇒ 셀 내 전 블록 NO_VEIN·RNG 미소비
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * OreVeinifier.canSkipCell(minD,maxD,cellMinY,cellMaxY)이 true를 반환하면, veinToggle이
 * [minD,maxD] bound 안에서 어떤 값을 취하더라도 셀의 128블록(4x4x8, 실제 cellWidth/cellHeight와
 * 동일 규모) 전부가 veinStateId()에서 NO_VEIN이어야 하고, 두 조기반환(:62,:67) 이전에 존재하는
 * random.at() 이후 로직(veinRidged/veinGap/RNG)은 절대 호출되지 않아야 한다(RNG 미소비 증명).
 */
class OreVeinifierSkipFuzzTest {

    private static final int CELL_WIDTH = 4;
    private static final int CELL_HEIGHT = 8;
    private static final long SEED = 42L;

    @Test
    void skipTrue_impliesAllBlocksNoVeinAndNoRngConsumption() {
        Random rng = new Random(SEED);
        int trials = 20_000;
        int skipHits = 0;

        for (int t = 0; t < trials; t++) {
            // 무작위 셀: y구간, veinToggle bound(부호 무작위 — copper/iron/straddle 골고루)
            int cellMinY = -80 + rng.nextInt(180); // -80..99
            int cellMaxY = cellMinY + CELL_HEIGHT - 1;

            double a = (rng.nextDouble() - 0.5) * 2.0; // [-1,1)
            double b = (rng.nextDouble() - 0.5) * 2.0;
            double minD = Math.min(a, b);
            double maxD = Math.max(a, b);

            boolean canSkip = OreVeinifier.canSkipCell(minD, maxD, cellMinY, cellMaxY);
            if (!canSkip) continue;
            skipHits++;

            int cellStartX = rng.nextInt(1000) - 500;
            int cellStartZ = rng.nextInt(1000) - 500;

            OreVeinifier veinifier = buildProbe(minD, maxD, cellMinY, cellMaxY, rng);

            for (int iy = 0; iy < CELL_HEIGHT; iy++) {
                int blockY = cellMinY + iy;
                for (int ix = 0; ix < CELL_WIDTH; ix++) {
                    for (int iz = 0; iz < CELL_WIDTH; iz++) {
                        int blockX = cellStartX + ix;
                        int blockZ = cellStartZ + iz;
                        DensityFunction.FunctionContext ctx =
                            new DensityFunction.SinglePointContext(blockX, blockY, blockZ);
                        int result;
                        try {
                            result = veinifier.veinStateId(ctx);
                        } catch (RngConsumedException e) {
                            fail("canSkipCell=true 인데 RNG가 소비됨: trial=" + t
                                + " minD=" + minD + " maxD=" + maxD
                                + " cellMinY=" + cellMinY + " cellMaxY=" + cellMaxY
                                + " blockY=" + blockY);
                            return;
                        }
                        assertEquals(OreVeinifier.NO_VEIN, result,
                            "canSkipCell=true 인데 NO_VEIN 아님: trial=" + t
                                + " minD=" + minD + " maxD=" + maxD
                                + " cellMinY=" + cellMinY + " cellMaxY=" + cellMaxY
                                + " blockY=" + blockY + " blockX=" + blockX + " blockZ=" + blockZ);
                    }
                }
            }
        }

        // 스킵 판정이 실제로 상당수 발생했는지(테스트가 공허하게 통과하지 않는지) 확인
        org.junit.jupiter.api.Assertions.assertTrue(skipHits > trials / 20,
            "skip 판정이 너무 드묾(" + skipHits + "/" + trials + ") — 파라미터 분포 재검토 필요");
    }

    /** RNG(random.at() 이후 경로)가 실제로 호출되면 던지는 감시용 예외. */
    private static final class RngConsumedException extends RuntimeException {}

    /**
     * veinToggle이 매 블록마다 [minD,maxD] 구간 내 무작위 값을 반환하는(경계 포함) OreVeinifier를
     * 생성한다. veinRidged/veinGap과 random.at()은 호출 시 RngConsumedException을 던져
     * "canSkipCell=true ⇒ random.at() 이후 로직 도달 안 함"을 강제 검증한다.
     */
    private static OreVeinifier buildProbe(double minD, double maxD, int cellMinY, int cellMaxY, Random rng) {
        DensityFunction veinToggle = new DensityFunction() {
            @Override public double compute(FunctionContext context) {
                // bound 경계값도 섞어 넣어 극단값 케이스를 커버
                int pick = rng.nextInt(4);
                if (pick == 0) return minD;
                if (pick == 1) return maxD;
                return minD + rng.nextDouble() * (maxD - minD);
            }
            @Override public double minValue() { return minD; }
            @Override public double maxValue() { return maxD; }
        };
        DensityFunction guarded = new DensityFunction() {
            @Override public double compute(FunctionContext context) { throw new RngConsumedException(); }
            @Override public double minValue() { return -1.0; }
            @Override public double maxValue() { return 1.0; }
        };
        XoroshiroRandomSource.XoroshiroPositionalRandomFactory guardedRandom =
            new XoroshiroRandomSource.XoroshiroPositionalRandomFactory(1L, 2L) {
                @Override public XoroshiroRandomSource at(int x, int y, int z) {
                    throw new RngConsumedException();
                }
            };
        return new OreVeinifier(veinToggle, guarded, guarded, guardedRandom,
            /*copperOre*/ 1, /*rawCopperBlock*/ 2, /*granite*/ 3,
            /*deepslateIronOre*/ 4, /*rawIronBlock*/ 5, /*tuff*/ 6);
    }
}
