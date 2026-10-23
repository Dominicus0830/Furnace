// 바닐라 RootedDirtBlock: 뿌리 내린 흙. 아래가 비었으면 뼛가루로 매달린 뿌리를 단다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class RootedDirtBlock extends BlockBehavior implements BonemealableBlock {

    private static final int HANGING_ROOTS = B.id("hanging_roots");

    public RootedDirtBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.below())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        level.setBlockAndUpdate(pos.below(), B.defaultState(HANGING_ROOTS));
    }
}
