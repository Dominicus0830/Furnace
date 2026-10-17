// 바닐라 PinkPetalsBlock: 분홍 꽃잎(1~4장). 같은 꽃잎을 더 놓으면 장 수가 는다. 뼛가루로 늘고, 4장이면 꽃잎 아이템을 떨군다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockItems;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class PinkPetalsBlock extends BushBlock implements BonemealableBlock {

    private static final Prop FACING = B.prop("facing"), AMOUNT = B.prop("flower_amount");

    public PinkPetalsBlock(int block) { super(block); }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.holding(block) && AMOUNT.intValue(state) < 4 || super.canBeReplaced(state, ctx);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos());
        return B.is(s, block) ? AMOUNT.withInt(s, Math.min(4, AMOUNT.intValue(s) + 1))
                : FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        int n = AMOUNT.intValue(state);
        if (n < 4) level.setBlock(pos, AMOUNT.withInt(state, n + 1), 2);
        else level.popResource(pos, new ItemStack(BlockItems.asItemId(block), 1));
    }
}
