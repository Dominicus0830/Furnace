// 바닐라 ObserverBlock: 관측기. 앞쪽(facing) 칸의 모양 갱신을 보면 2틱 뒤 2틱 동안 뒤쪽으로 강한 신호.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class ObserverBlock extends DirectionalBlock {

    private static final Prop POWERED = B.prop("powered");

    public ObserverBlock(int block) { super(block); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (POWERED.is(state)) {
            level.setBlock(pos, POWERED.with(state, false), 2);
        } else {
            level.setBlock(pos, POWERED.with(state, true), 2);
            level.scheduleTick(pos, block, 2);
        }
        updateNeighborsInFront(level, pos, state);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (facing(state) == dir && !POWERED.is(state)) startSignal(level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    private void startSignal(BlockLevel level, Pos pos) {
        if (!level.hasScheduledTick(pos, block)) level.scheduleTick(pos, block, 2);
    }

    /** updateNeighborsInFront: 등 뒤 칸에 알리고, 그 칸의 이웃(이 관측기 쪽 제외)에도. */
    protected void updateNeighborsInFront(BlockLevel level, Pos pos, int state) {
        int f = facing(state);
        Pos behind = pos.relative(Dir.opposite(f));
        level.neighborChanged(behind, block, pos);
        level.updateNeighborsAtExceptFromFacing(behind, block, f);
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return B.behavior(state).getSignal(state, level, pos, dir); }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) && facing(state) == dir ? 15 : 0; }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state)) && POWERED.is(state) && !level.hasScheduledTick(pos, block)) {
            int s = POWERED.with(state, false);
            level.setBlock(pos, s, 18);
            updateNeighborsInFront(level, pos, s);
        }
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, B.block(state)) && POWERED.is(state) && level.hasScheduledTick(pos, block)) {
            updateNeighborsInFront(level, pos, POWERED.with(state, false));
        }
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return withFacing(defaultState(), ctx.nearestLookingDirection()); }
}
