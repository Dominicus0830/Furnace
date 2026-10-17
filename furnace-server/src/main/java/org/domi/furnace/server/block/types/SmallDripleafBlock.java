// 바닐라 SmallDripleafBlock: 두 칸 작은 흘림잎. 놓을 수 있는 땅이거나 물속 흙 위. 뼛가루로 큰 흘림잎이 된다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class SmallDripleafBlock extends DoublePlantBlock implements BonemealableBlock {

    private static final Prop FACING = B.prop("facing");

    public SmallDripleafBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isTag(state, "minecraft:small_dripleaf_placeable")
                || Fluids.isWaterSource(level.getFluidState(pos.above())) && super.mayPlaceOn(state, level, pos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = super.getStateForPlacement(ctx);
        return s < 0 ? -1 : copyWaterloggedFrom(ctx.level(), ctx.clickedPos(), FACING.with(s, Dir.NAME[Dir.opposite(ctx.horizontalDirection())]));
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        Pos above = pos.above();
        int upper = FACING.with(HALF.with(defaultState(), UPPER), FACING.valueName(state));
        level.setBlock(above, copyWaterloggedFrom(level, above, upper), 3);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        if (UPPER.equals(HALF.valueName(state))) return super.canSurvive(state, level, pos);
        Pos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        if (lower(state)) {
            Pos above = pos.above();
            level.setBlock(above, Fluids.createLegacyBlock(level.getFluidState(above)), 18);
            BigDripleafBlock.placeWithRandomHeight(level, random, pos, Dir.byName(FACING.valueName(state)));
        } else {
            Pos below = pos.below();
            performBonemeal(level, random, below, level.getBlockState(below));
        }
    }
}
