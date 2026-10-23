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
        // TODO(빛): (random.nextInt(3) == 0 || 이웃 4 미만) && getMaxLocalRawBrightness(pos) > 11 - age - lightBlock && slightlyMelt
        //  → 녹으면 이웃 얼음에도 틱. 빛 계산 전에는 녹지 않고 다시 예약만 한다(난수 순서는 같게).
        random.nextInt(3);
        level.scheduleTick(pos, block, nextInt(random, 20, 40));
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
