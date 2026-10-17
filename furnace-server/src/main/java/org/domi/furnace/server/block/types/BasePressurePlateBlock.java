// 바닐라 BasePressurePlateBlock: 압력판 바탕. 아래가 받쳐야 산다. 엔티티가 올라서면 켜지고, 비면 다음 틱에 꺼진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.BlockSetType;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public abstract class BasePressurePlateBlock extends BlockBehavior {

    protected final BlockSetType type;

    protected BasePressurePlateBlock(int block) {
        super(block);
        this.type = BlockSetType.of(block);
    }

    protected int pressedTime() { return 20; }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return B.canSupportRigidBlock(level, below) || B.canSupportCenter(level, below, Dir.UP);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        int signal = signalForState(state);
        if (signal > 0) checkPressed(null, level, pos, state, signal);
    }

    // TODO(엔티티): entityInside — 꺼져 있을 때 엔티티가 닿으면 checkPressed.

    private void checkPressed(BlockActor entity, BlockLevel level, Pos pos, int state, int old) {
        int signal = signalStrength(level, pos);
        boolean was = old > 0, now = signal > 0;
        if (old != signal) {
            level.setBlock(pos, setSignalForState(state, signal), 2);
            updateNeighbours(level, pos);
        }
        if (!now && was) {
            level.playSound(null, pos, type.pressurePlateClickOff(), "blocks", 1.0F, 1.0F);
            level.gameEvent("minecraft:block_deactivate", pos, entity, -1);
        } else if (now && !was) {
            level.playSound(null, pos, type.pressurePlateClickOn(), "blocks", 1.0F, 1.0F);
            level.gameEvent("minecraft:block_activate", pos, entity, -1);
        }
        if (now) level.scheduleTick(pos, block, pressedTime());
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved && !B.is(newState, block)) {
            if (signalForState(state) > 0) updateNeighbours(level, pos);
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    protected void updateNeighbours(BlockLevel level, Pos pos) {
        level.updateNeighborsAt(pos, block);
        level.updateNeighborsAt(pos.below(), block);
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return signalForState(state); }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return dir == Dir.UP ? signalForState(state) : 0; }

    @Override
    public boolean isSignalSource(int state) { return true; }

    /** getSignalStrength: 판 위 상자 안 엔티티 수에 따른 세기. TODO(엔티티): 엔티티 체계 전에는 언제나 0. */
    protected abstract int signalStrength(BlockLevel level, Pos pos);

    protected abstract int signalForState(int state);

    protected abstract int setSignalForState(int state, int signal);
}
