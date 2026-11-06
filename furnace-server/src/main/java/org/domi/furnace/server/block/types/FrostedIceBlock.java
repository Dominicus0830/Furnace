// 바닐라 FrostedIceBlock: 차가운 걸음이 만든 얼음. 놓이면 60~120틱 뒤 틱, 밝거나 이웃이 적으면 단계적으로 녹는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class FrostedIceBlock extends IceBlock {

    private static final Prop AGE = B.prop("age");

    public FrostedIceBlock(int block) { super(block); }

    /** Mth.nextInt(random, min, max). */
    static int nextInt(RandomSource r, int min, int max) { return min >= max ? min : r.nextInt(max - min + 1) + min; }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        level.scheduleTick(pos, block, nextInt(level.random(), 60, 120));
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        // (random.nextInt(3) == 0 || 이웃 4 미만) && getMaxLocalRawBrightness(pos) > 11 - age - lightBlock && slightlyMelt
        //   → 녹았으면 이웃 얼음도 녹여 보고(안 녹으면 다시 예약). 아니면 20~40틱 뒤 다시.
        if ((random.nextInt(3) == 0 || fewerNeighboursThan(level, pos, 4))
                && level.getMaxLocalRawBrightness(pos) > 11 - AGE.intValue(state) - LIGHT_BLOCK
                && slightlyMelt(state, level, pos)) {
            for (int d : Dir.ALL) {
                Pos n = pos.relative(d);
                int ns = level.getBlockState(n);
                if (B.is(ns, block) && !slightlyMelt(ns, level, n)) level.scheduleTick(n, block, nextInt(random, 20, 40));
            }
        } else {
            level.scheduleTick(pos, block, nextInt(random, 20, 40));
        }
    }

    /** slightlyMelt: 나이 +1, 3 이면 녹는다. */
    private boolean slightlyMelt(int state, BlockLevel level, Pos pos) {
        int age = AGE.intValue(state);
        if (age < 3) {
            level.setBlock(pos, AGE.withInt(state, age + 1), 2);
            return false;
        }
        melt(state, level, pos);
        return true;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (sourceBlock == block && fewerNeighboursThan(level, pos, 2)) melt(state, level, pos);
        super.neighborChanged(state, level, pos, sourceBlock, sourcePos, moved);
    }

    private boolean fewerNeighboursThan(BlockLevel level, Pos pos, int n) {
        int c = 0;
        for (int d : Dir.ALL) {
            if (B.is(level.getBlockState(pos.relative(d)), block) && ++c >= n) return false;
        }
        return true;
    }
}
