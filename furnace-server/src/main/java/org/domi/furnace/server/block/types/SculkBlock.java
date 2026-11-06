// 바닐라 SculkBlock: 스컬크(경험치 1). 스컬크 촉매의 퍼짐(SculkSpreader)이 이 블록 위에서 전하를 써서 감지체·비명체를 키운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SculkBlock extends DropExperienceBlock implements SculkBehaviour {

    private static final int SENSOR = B.id("sculk_sensor"), SHRIEKER = B.id("sculk_shrieker"), WATER = B.id("water");
    private static final Prop CAN_SUMMON = B.prop("can_summon"), WATERLOGGED_P = B.prop("waterlogged");

    public SculkBlock(int block) { super(block, 1, 1); }

    @Override
    public int attemptUseCharge(SculkSpreader.ChargeCursor cursor, BlockLevel level, Pos pos, RandomSource random, SculkSpreader spreader,
                                boolean shouldConvertBlocks) {
        int i = cursor.getCharge();
        if (i != 0 && random.nextInt(spreader.chargeDecayRate()) == 0) {
            Pos cp = cursor.getPos();
            boolean close = distSqr(cp, pos) < (double) spreader.noGrowthRadius() * spreader.noGrowthRadius();
            if (!close && canPlaceGrowth(level, cp)) {
                int j = spreader.growthSpawnCost();
                if (random.nextInt(j) < i) {
                    Pos up = cp.above();
                    int bs = getRandomGrowthState(level, up, random, spreader.isWorldGeneration());
                    level.setBlock(up, bs, 3);
                    level.playSound(null, cp, INFO.placeSound(B.block(bs)), "blocks", 1.0F, 1.0F);
                }
                return Math.max(0, i - j);
            }
            return random.nextInt(spreader.additionalDecayRate()) != 0 ? i : i - (close ? 1 : getDecayPenalty(spreader, cp, pos, i));
        }
        return i;
    }

    /** Vec3i.distSqr(아래 모서리끼리). */
    private static double distSqr(Pos a, Pos b) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int getDecayPenalty(SculkSpreader spreader, Pos cursorPos, Pos rootPos, int charge) {
        int i = spreader.noGrowthRadius();
        float d = (float) Math.sqrt(distSqr(cursorPos, rootPos)) - i;
        float f = d * d;
        int j = (24 - i) * (24 - i);
        float g = Math.min(1.0F, f / j);
        return Math.max(1, (int) (charge * g * 0.5F));
    }

    private static int getRandomGrowthState(BlockLevel level, Pos pos, RandomSource random, boolean isWorldGeneration) {
        int bs = random.nextInt(11) == 0 ? CAN_SUMMON.with(B.defaultState(SHRIEKER), isWorldGeneration) : B.defaultState(SENSOR);
        return WATERLOGGED_P.has(bs) && !Fluids.isEmpty(level.getFluidState(pos)) ? WATERLOGGED_P.with(bs, true) : bs;
    }

    private static boolean canPlaceGrowth(BlockLevel level, Pos pos) {
        int above = level.getBlockState(pos.above());
        if (B.isAir(above) || B.is(above, WATER) && Fluids.isWater(B.fluid(above))) {
            int count = 0;
            for (int z = -4; z <= 4; z++)
                for (int y = 0; y <= 2; y++)
                    for (int x = -4; x <= 4; x++) {
                        int s = level.getBlockState(pos.offset(x, y, z));
                        if (B.is(s, SENSOR) || B.is(s, SHRIEKER)) count++;
                        if (count > 2) return false;
                    }
            return true;
        }
        return false;
    }

    @Override
    public boolean canChangeBlockStateOnSpread() { return false; }
}
