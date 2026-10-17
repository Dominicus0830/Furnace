// 바닐라 DetectorRailBlock: 감지 레일. 광산 수레가 지나가면 신호(엔티티 체계 전에는 수레가 없어 늘 꺼진다).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class DetectorRailBlock extends BaseRailBlock {

    private static final Prop POWERED = B.prop("powered");

    public DetectorRailBlock(int block) { super(block, true); }

    @Override
    public boolean isSignalSource(int state) { return true; }

    // TODO(엔티티): entityInside — 꺼져 있을 때 수레가 닿으면 checkPressed.

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (POWERED.is(state)) checkPressed(level, pos, state);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) ? 15 : 0; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) && dir == Dir.UP ? 15 : 0; }

    private void checkPressed(BlockLevel level, Pos pos, int state) {
        if (!canSurvive(state, level, pos)) return;
        boolean was = POWERED.is(state);
        boolean pressed = false;   // TODO(엔티티): 칸 안(0.2 안쪽 상자)의 광산 수레
        if (pressed && !was) {
            int s = POWERED.with(state, true);
            level.setBlock(pos, s, 3);
            updatePowerToConnected(level, pos, s);
            level.updateNeighborsAt(pos, block);
            level.updateNeighborsAt(pos.below(), block);
        }
        if (!pressed && was) {
            int s = POWERED.with(state, false);
            level.setBlock(pos, s, 3);
            updatePowerToConnected(level, pos, s);
            level.updateNeighborsAt(pos, block);
            level.updateNeighborsAt(pos.below(), block);
        }
        if (pressed) level.scheduleTick(pos, block, 20);
        level.updateNeighbourForOutputSignal(pos, block);
    }

    private static void updatePowerToConnected(BlockLevel level, Pos pos, int state) {
        for (Pos p : new RailState(level, pos, state).connections()) {
            int s = level.getBlockState(p);
            level.neighborChanged(s, p, B.block(s), pos, false);
        }
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state))) {
            int s = updateState(state, level, pos, moved);
            checkPressed(level, pos, s);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }   // TODO(엔티티): 명령·상자 수레
}
