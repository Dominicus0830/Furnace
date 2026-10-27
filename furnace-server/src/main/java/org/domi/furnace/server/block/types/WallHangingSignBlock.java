// 바닐라 WallHangingSignBlock: 벽에 거는 표지판. 양옆 중 한쪽이 단단한 면이거나 같은 축의 벽걸이 표지판이어야 놓인다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockPlacement;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class WallHangingSignBlock extends SignBlock implements BlockPlacement.WallHangingSignPlacement {

    static final Prop FACING = B.prop("facing");

    public WallHangingSignBlock(int block) { super(block); }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public float yRotationDegrees(int state) { return (Dir.to2D(facing(state)) & 3) * 90; }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        // shouldTryToChainAnotherHangingSign: 매단 표지판을 들고 판 앞뒤가 아닌 면을 클릭.
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.SignBlockEntity sign
                && !sign.canExecuteClickCommands(isFacingFrontText(sign, player), player.isTextFilteringEnabled())
                && isHangingSignItem(stack) && Dir.axis(hit.face()) != Dir.axis(facing(state))) return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    /** canPlace: 시계·반시계 쪽 칸 중 하나에 걸 수 있는가. */
    @Override
    public boolean canPlace(int state, BlockLevel level, Pos pos) {
        int cw = Dir.clockWise(facing(state)), ccw = Dir.counterClockWise(facing(state));
        return canAttachTo(level, state, pos.relative(cw), ccw) || canAttachTo(level, state, pos.relative(ccw), cw);
    }

    /** canAttachTo: 벽걸이 표지판이면 같은 축, 아니면 그 면이 단단(FULL). */
    public boolean canAttachTo(BlockLevel level, int state, Pos pos, int dir) {
        int s = level.getBlockState(pos);
        return B.isTag(s, "minecraft:wall_hanging_signs") ? Dir.axis(facing(s)) == Dir.axis(facing(state)) : B.isFaceSturdy(s, level, pos, dir);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = defaultState();
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int clickedAxis = Dir.axis(ctx.clickedFace());
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d) && Dir.axis(d) != clickedAxis) {
                s = FACING.with(s, Dir.NAME[Dir.opposite(d)]);
                if (B.canSurvive(s, level, pos) && canPlace(s, level, pos)) return WATERLOGGED.with(s, B.waterAt(ctx));
            }
        }
        return -1;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return Dir.axis(dir) == Dir.axis(Dir.clockWise(facing(state))) && !canSurvive(state, level, pos)
                ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
