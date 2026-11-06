// 바닐라 CandleBlock: 양초(candles 1..4, lit, waterlogged). 같은 양초를 들고 누르면 늘고, 빈손으로 누르면 끈다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class CandleBlock extends AbstractCandleBlock {

    static final Prop CANDLES = B.prop("candles");

    public CandleBlock(int block) { super(block); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (stack.isEmpty() && player.mayBuild() && LIT.is(state)) {
            extinguish(player, state, level, pos);
            return ItemInteractionResult.sidedSuccess(false);
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.holding(block) && CANDLES.intValue(state) < 4 || super.canBeReplaced(state, ctx);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos());
        if (B.is(s, block)) return CANDLES.cycle(s);
        return WATERLOGGED.with(super.getStateForPlacement(ctx), B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** placeLiquid: 물 원천이 들어오면 잠그고, 켜져 있었으면 끈다(extinguish 가 잠긴 상태를 쓴다). 물 틱. */
    @Override
    public boolean placeLiquid(BlockLevel level, Pos pos, int state, int fluidState) {
        if (WATERLOGGED.is(state) || org.domi.furnace.server.block.Fluids.type(fluidState) != org.domi.furnace.server.block.Fluids.T_WATER) return false;
        int s = WATERLOGGED.with(state, true);
        if (LIT.is(state)) extinguish(null, s, level, pos);
        else level.setBlock(pos, s, 3);
        level.scheduleFluidTick(pos, org.domi.furnace.server.block.Fluids.T_WATER, org.domi.furnace.server.block.Fluids.tickDelay(fluidState, level.ultraWarm()));
        return true;
    }

    /** canLight: 꺼져 있고 잠기지 않은 양초. */
    public static boolean canLight(int state) {
        return B.isTag(state, "minecraft:candles") && LIT.has(state) && WATERLOGGED.has(state) && !LIT.is(state) && !WATERLOGGED.is(state);
    }

    @Override
    protected boolean canBeLit(int state) { return !WATERLOGGED.is(state) && super.canBeLit(state); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.canSupportCenter(level, pos.below(), Dir.UP); }
}
