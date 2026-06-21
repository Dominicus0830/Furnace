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
}
