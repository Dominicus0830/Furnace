// 바닐라 OreVeinifier 비트정확 포팅 — solid 블록에 copper/iron 광맥 state id를 override
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

/**
 * 바닐라 net.minecraft.world.level.levelgen.OreVeinifier 비트정확 포팅.
 * NoiseChunk material rule의 oreVeinifier 단계 — solid(final_density>0) 블록서만 호출.
 *
 * 소비순서(단락평가 정확): veinToggle.compute → rand=oreRandom.at(x,i,z) → nextFloat(solidness,>0.7컷)
 *   → veinRidged.compute(>=0컷) → nextFloat(richness,<d2) → veinGap.compute(>-0.3) → nextFloat(rawChance,<0.02).
 * no-vein early return 경로선 후속 노이즈/난수 미소비 = byte-exact 핵심.
 */
public final class OreVeinifier {

    public static final int NO_VEIN = -1;

    // vein y범위 합집합(copper 0~50, iron -60~-8) → [-60,50]. all-stone 셀skip 비활성 판정용.
    static final int VEIN_MIN_Y = -60;
    static final int VEIN_MAX_Y = 50;

    // veinStateId(:54-55)와 동일한 copper/iron 개별 y범위 — NoiseChunk 셀 단위 NO_VEIN 조기판정용.
    static final int COPPER_MIN_Y = 0;
    static final int COPPER_MAX_Y = 50;
    static final int IRON_MIN_Y = -60;
    static final int IRON_MAX_Y = -8;

    private final DensityFunction veinToggle;
    private final DensityFunction veinRidged;
    private final DensityFunction veinGap;
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory random;

    private final int copperOre, rawCopperBlock, granite;
    private final int deepslateIronOre, rawIronBlock, tuff;

    public OreVeinifier(DensityFunction veinToggle, DensityFunction veinRidged, DensityFunction veinGap,
                        XoroshiroRandomSource.XoroshiroPositionalRandomFactory random,
                        int copperOre, int rawCopperBlock, int granite,
                        int deepslateIronOre, int rawIronBlock, int tuff) {
        this.veinToggle = veinToggle;
        this.veinRidged = veinRidged;
        this.veinGap = veinGap;
        this.random = random;
        this.copperOre = copperOre;
        this.rawCopperBlock = rawCopperBlock;
        this.granite = granite;
        this.deepslateIronOre = deepslateIronOre;
        this.rawIronBlock = rawIronBlock;
        this.tuff = tuff;
    }

    /** vein state id, 또는 NO_VEIN(-1)=광맥없음(호출자는 stoneId 유지). context는 NoiseChunk(셀 보간). */
    public int veinStateId(DensityFunction.FunctionContext context) {
        double d = veinToggle.compute(context);
        int i = context.blockY();
        boolean copper = d > 0.0;
        double abs = Math.abs(d);

        int minY = copper ? 0 : -60;
        int maxY = copper ? 50 : -8;
        int ore = copper ? copperOre : deepslateIronOre;
        int raw = copper ? rawCopperBlock : rawIronBlock;
        int filler = copper ? granite : tuff;

        int i1 = maxY - i;
        int i2 = i - minY;
        if (i2 < 0 || i1 < 0) {
            return NO_VEIN;
        }
        int min = Math.min(i1, i2);
        double d1 = MathHelper.clampedMap((double) min, 0.0, 20.0, -0.2, 0.0);
        if (abs + d1 < 0.4F) {
            return NO_VEIN;
        }
        XoroshiroRandomSource rand = random.at(context.blockX(), i, context.blockZ());
        if (rand.nextFloat() > 0.7F) {
            return NO_VEIN;
        }
        if (veinRidged.compute(context) >= 0.0) {
            return NO_VEIN;
        }
        double d2 = MathHelper.clampedMap(abs, 0.4F, 0.6F, 0.1F, 0.3F);
        if (rand.nextFloat() < d2 && veinGap.compute(context) > -0.3F) {
            return rand.nextFloat() < 0.02F ? raw : ore;
        }
        return filler;
    }

    /**
     * NoiseChunk 셀 단위 조기스킵 판정(순수 함수, NoiseChunk 의존 없음 — 직접 fuzz 테스트용).
     * veinToggle의 셀 bound[minD,maxD]와 셀 블록 y구간[cellMinY,cellMaxY]만으로, 셀 전체 블록이
     * veinStateId()의 두 조기반환(:62 i1&lt;0||i2&lt;0, :67 abs+d1&lt;0.4)에 의해 NO_VEIN임을
     * 증명할 수 있으면 true. RNG 소비(random.at() 이후)는 이 두 조기반환 이후에만 발생하므로
     * 스킵은 RNG/출력 불변.
     *
     * 증명:
     *  1) bound가 0을 걸치면(부호 불명) 블록마다 copper/iron이 달라질 수 있어 폴백(false).
     *  2) 셀 y구간이 해당 copper/iron의 [minY,maxY]와 아예 안 겹치면(yLo&gt;yHi) 모든 블록이
     *     i1&lt;0||i2&lt;0(:62)로 NO_VEIN — d값과 무관하게 항상 성립.
     *  3) 겹치는 구간 [yLo,yHi]에서는 d1(i)=clampedMap(min(maxY-i,i-minY),0,20,-0.2,0.0)이
     *     min(...)의 증가함수이고, min(maxY-i,i-minY)는 i*=(minY+maxY)/2에서 최대인 오목(텐트)
     *     함수이므로 [yLo,yHi] 내 최댓값은 i*를 그 구간으로 clamp한 floor/ceil 두 점 중 하나에서
     *     나온다(concave 함수 성질 — i*가 구간 안이면 그 근방 정수, 밖이면 구간 쪽 끝점으로 clamp됨).
     *     maxAbs(=bound에서 유도된 |d| 상한) + 그 d1 최댓값 &lt; 0.4 이면 셀 전체 블록에서 (:67) 성립.
     */
    public static boolean canSkipCell(double minD, double maxD, int cellMinY, int cellMaxY) {
        boolean copper;
        if (minD > 0.0) {
            copper = true;
        } else if (maxD <= 0.0) {
            copper = false;
        } else {
            return false; // bound가 0을 걸침 → copper/iron 블록별로 갈릴 수 있음, 폴백
        }

        int minY = copper ? COPPER_MIN_Y : IRON_MIN_Y;
        int maxY = copper ? COPPER_MAX_Y : IRON_MAX_Y;

        int yLo = Math.max(cellMinY, minY);
        int yHi = Math.min(cellMaxY, maxY);
        if (yLo > yHi) {
            return true; // 셀 y구간 전체가 [minY,maxY] 밖 → 항상 NO_VEIN
        }

        double maxAbs = Math.max(Math.abs(minD), Math.abs(maxD));

        long iStarFloor = Math.floorDiv((long) minY + maxY, 2L);
        int c1 = MathHelper.clamp((int) iStarFloor, yLo, yHi);
        int c2 = MathHelper.clamp((int) iStarFloor + 1, yLo, yHi);
        double mMax = Math.max(veinM(c1, minY, maxY), veinM(c2, minY, maxY));
        double d1Max = MathHelper.clampedMap(mMax, 0.0, 20.0, -0.2, 0.0);

        return maxAbs + d1Max < 0.4;
    }

    /** veinStateId(:65) min(i1,i2)와 동일한 텐트함수. */
    private static double veinM(int i, int minY, int maxY) {
        return Math.min(maxY - i, i - minY);
    }
}
