// 바닐라 BaseRailBlock: 레일 바탕. 아래 윗면이 단단(RIGID)해야 산다. 놓이면 RailState 로 주변 레일과 이어 모양을 정한다.
// isStraight(전동·감지 레일)는 꺾이지 않고, 놓이거나 신호가 바뀌면 자기 자리에 이웃 알림을 한 번 더 낸다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public abstract class BaseRailBlock extends BlockBehavior {

    static final Prop SHAPE = B.prop("shape");
    private final boolean isStraight;

    protected BaseRailBlock(int block, boolean isStraight) {
        super(block);
        this.isStraight = isStraight;
    }

    public boolean isStraight() { return isStraight; }

    /** isRail(level, pos)·isRail(state): #rails 이고 레일 동작. */
    public static boolean isRail(BlockLevel level, Pos pos) { return isRail(level.getBlockState(pos)); }

    public static boolean isRail(int state) { return B.isTag(state, "minecraft:rails") && B.behavior(state) instanceof BaseRailBlock; }

    static String shape(int state) { return SHAPE.valueName(state); }

    static boolean ascending(String shape) { return shape.startsWith("ascending_"); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.canSupportRigidBlock(level, pos.below()); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state))) updateState(state, level, pos, moved);
    }

    /** updateState(state, level, pos, moved): 모양을 다시 잡고, 곧은 레일이면 자기 자리에 이웃 알림. */
    protected int updateState(int state, BlockLevel level, Pos pos, boolean moved) {
        state = updateDir(level, pos, state, true);
        if (isStraight) level.neighborChanged(state, pos, block, pos, moved);
        return state;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (B.is(level.getBlockState(pos), block)) {
            if (shouldBeRemoved(pos, level, shape(state))) {
                // TODO(드롭): Block.dropResources(state, level, pos)
                level.removeBlock(pos, moved);
            } else {
                updateState(state, level, pos, sourceBlock);
            }
        }
    }

    private static boolean shouldBeRemoved(Pos pos, BlockLevel level, String shape) {
        if (!B.canSupportRigidBlock(level, pos.below())) return true;
        return switch (shape) {
            case "ascending_east" -> !B.canSupportRigidBlock(level, pos.east());
            case "ascending_west" -> !B.canSupportRigidBlock(level, pos.west());
            case "ascending_north" -> !B.canSupportRigidBlock(level, pos.north());
            case "ascending_south" -> !B.canSupportRigidBlock(level, pos.south());
            default -> false;
        };
    }

    /** updateState(state, level, pos, 이웃 블록): 종류별 신호 반응. */
    protected void updateState(int state, BlockLevel level, Pos pos, int sourceBlock) {}

    /** updateDir: RailState.place(신호, 언제나 놓기, 지금 모양). */
    protected int updateDir(BlockLevel level, Pos pos, int state, boolean alwaysPlace) {
        return new RailState(level, pos, state).place(level.hasNeighborSignal(pos), alwaysPlace, shape(state)).state();
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved) {
            super.onRemove(state, level, pos, newState, moved);
            if (ascending(shape(state))) level.updateNeighborsAt(pos.above(), block);
            if (isStraight) {
                level.updateNeighborsAt(pos, block);
                level.updateNeighborsAt(pos.below(), block);
            }
        }
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int dir = ctx.horizontalDirection();
        boolean ew = dir == Dir.EAST || dir == Dir.WEST;
        int s = SHAPE.with(defaultState(), ew ? "east_west" : "north_south");
        return WATERLOGGED.with(s, B.waterAt(ctx));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
