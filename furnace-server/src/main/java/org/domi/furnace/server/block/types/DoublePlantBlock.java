// 바닐라 DoublePlantBlock: 두 칸 식물. 아래·위 반쪽이 서로를 붙잡는다. 위 반쪽을 부수면 아래 반쪽이 드롭 없이 사라진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class DoublePlantBlock extends BushBlock {

    static final Prop HALF = B.prop("half");
    static final String UPPER = "upper", LOWER = "lower";

    public DoublePlantBlock(int block) { super(block); }

    static boolean lower(int state) { return LOWER.equals(HALF.valueName(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        boolean lower = lower(state);
        if (Dir.axis(dir) != Dir.AXIS_Y || lower != (dir == Dir.UP) || B.is(neighborState, block) && lower(neighborState) != lower) {
            return lower && dir == Dir.DOWN && !canSurvive(state, level, pos)
                    ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        }
        return B.defaultState("air");
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        Pos pos = ctx.clickedPos();
        BlockLevel level = ctx.level();
        return pos.y() < level.maxBuildHeight() - 1 && B.canBeReplaced(level.getBlockState(pos.above()), ctx) ? super.getStateForPlacement(ctx) : -1;
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        Pos above = pos.above();
        level.setBlock(above, copyWaterloggedFrom(level, above, HALF.with(defaultState(), UPPER)), 3);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        if (!UPPER.equals(HALF.valueName(state))) return super.canSurvive(state, level, pos);
        int below = level.getBlockState(pos.below());
        return B.is(below, block) && lower(below);
    }

    /** placeAt(level, state, pos, flags): 아래·위 반쪽을 함께 놓는다(지형 생성·뼛가루). */
    public static void placeAt(BlockLevel level, int state, Pos pos, int flags) {
        Pos above = pos.above();
        level.setBlock(pos, copyWaterloggedFrom(level, pos, HALF.with(state, LOWER)), flags);
        level.setBlock(above, copyWaterloggedFrom(level, above, HALF.with(state, UPPER)), flags);
    }

    /** copyWaterloggedFrom: waterlogged 속성이 있으면 그 칸이 물인지 옮긴다. */
    public static int copyWaterloggedFrom(BlockLevel level, Pos pos, int state) {
        return WATERLOGGED.has(state) ? WATERLOGGED.with(state, level.isWaterAt(pos)) : state;
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (player.isCreative()) preventDropFromBottomPart(level, pos, state, player);
        // else: TODO(드롭) Block.dropResources(state, level, pos, null, player, 손 도구) — 위 반쪽을 부숴도 드롭은 여기서
        return super.playerWillDestroy(level, pos, state, player);
    }

    // TODO(드롭): playerDestroy — 공기 상태로 super.playerDestroy(이미 위에서 드롭했으므로).

    /** preventDropFromBottomPart: 위 반쪽이면 아래 반쪽을 드롭 없이 지운다(물이면 물로). */
    protected static void preventDropFromBottomPart(BlockLevel level, Pos pos, int state, BlockActor player) {
        if (UPPER.equals(HALF.valueName(state))) {
            Pos below = pos.below();
            int bs = level.getBlockState(below);
            if (B.is(bs, B.block(state)) && lower(bs)) {
                int replace = Fluids.isWaterSource(B.fluid(bs)) ? B.defaultState("water") : B.defaultState("air");
                level.setBlock(below, replace, 35);
                level.levelEvent(player, 2001, below, bs);
            }
        }
    }
}
