// 바닐라 SpreadingSnowyDirtBlock: 퍼지는 흙(잔디·균사체). 위가 빛을 막으면 흙으로 돌아가고, 밝으면 둘레 흙으로 번진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public abstract class SpreadingSnowyDirtBlock extends SnowyDirtBlock {

    private static final int SNOW = B.id("snow"), DIRT = B.id("dirt");

    protected SpreadingSnowyDirtBlock(int block) { super(block); }

    /** canBeGrass: 위가 눈 한 겹이면 참, 가득 찬 유체면 거짓, 아니면 위 칸이 빛을 다 막지 않는가. */
    private static boolean canBeGrass(int state, BlockLevel level, Pos pos) {
        int above = level.getBlockState(pos.above());
        if (B.is(above, SNOW) && SnowLayerBlock.LAYERS.intValue(above) == 1) return true;
        if (Fluids.amount(B.fluid(above)) == 8) return false;
        // LightEngine.getLightBlockInto(…, UP, above.getLightBlock) < 최대 빛. TODO(빛): 빛 차단량 표가 생기면 정확히.
        return !B.lightBlockedAbove(above);
    }

    private static boolean canPropagate(int state, BlockLevel level, Pos pos) {
        return canBeGrass(state, level, pos) && !Fluids.isWater(level.getFluidState(pos.above()));
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canBeGrass(state, level, pos)) {
            if (B.fade(level, pos, B.defaultState(DIRT))) level.setBlockAndUpdate(pos, B.defaultState(DIRT));   // 플러그인 BlockFadeEvent
            return;
        }
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9) {
            int def = defaultState();
            for (int i = 0; i < 4; i++) {
                int dx = random.nextInt(3) - 1, dy = random.nextInt(5) - 3, dz = random.nextInt(3) - 1;
                Pos p = pos.offset(dx, dy, dz);
                if (B.is(level.getBlockState(p), DIRT) && canPropagate(def, level, p)) {
                    B.spread(level, pos, p, SNOWY.with(def, B.is(level.getBlockState(p.above()), SNOW)), 3);   // 플러그인 BlockSpreadEvent
                }
            }
        }
    }
}
