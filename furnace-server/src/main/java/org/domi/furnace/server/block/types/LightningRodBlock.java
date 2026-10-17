// 바닐라 LightningRodBlock: 피뢰침. 벼락을 맞으면 8틱 동안 붙은 칸에 강한 신호. 물에 잠길 수 있다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class LightningRodBlock extends DirectionalBlock {

    private static final Prop POWERED = B.prop("powered");

    public LightningRodBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return WATERLOGGED.with(withFacing(defaultState(), ctx.clickedFace()), B.waterAt(ctx)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) ? 15 : 0; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) && facing(state) == dir ? 15 : 0; }

    /** onLightningStrike: 켜고 이웃 알림, 8틱 뒤 꺼짐, 전기 불꽃 이벤트(3002, 축). */
    public void onLightningStrike(int state, BlockLevel level, Pos pos) {
        level.setBlock(pos, POWERED.with(state, true), 3);
        updateNeighbours(state, level, pos);
        level.scheduleTick(pos, block, 8);
        level.levelEvent(null, 3002, pos, Dir.axis(facing(state)));
    }

    private void updateNeighbours(int state, BlockLevel level, Pos pos) { level.updateNeighborsAt(pos.relative(Dir.opposite(facing(state))), block); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        level.setBlock(pos, POWERED.with(state, false), 3);
        updateNeighbours(state, level, pos);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            if (POWERED.is(state)) updateNeighbours(state, level, pos);
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, block) && POWERED.is(state) && !level.hasScheduledTick(pos, block)) {
            level.setBlock(pos, POWERED.with(state, false), 18);
        }
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    // TODO(엔티티·날씨): onProjectileHit — 뇌우 중 삼지창에 맞으면 벼락.
}
