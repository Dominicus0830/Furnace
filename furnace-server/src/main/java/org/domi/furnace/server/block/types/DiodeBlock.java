// 바닐라 DiodeBlock: 중계기·비교기 바탕. 뒤(facing 쪽)에서 신호를 받아 앞(반대쪽)으로 내고, 옆 신호는 잠금·비교에 쓴다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockInfo;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public abstract class DiodeBlock extends HorizontalDirectionalBlock {

    static final Prop POWERED = B.prop("powered"), WIRE_POWER = B.prop("power");
    /** TickPriority: EXTREMELY_HIGH -3, VERY_HIGH -2, HIGH -1, NORMAL 0. */
    static final int EXTREMELY_HIGH = -3, VERY_HIGH = -2, HIGH = -1, NORMAL = 0;
    private static final int REDSTONE_WIRE = B.id("redstone_wire"), REDSTONE_BLOCK = B.id("redstone_block");

    protected DiodeBlock(int block) { super(block); }

    static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return canSurviveOn(level, below, level.getBlockState(below));
    }

    protected boolean canSurviveOn(BlockLevel level, Pos pos, int state) { return B.isFaceSturdy(state, level, pos, Dir.UP, BlockInfo.RIGID); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (isLocked(level, pos, state)) return;
        boolean powered = POWERED.is(state);
        boolean should = shouldTurnOn(level, pos, state);
        if (powered && !should) {
            level.setBlock(pos, POWERED.with(state, false), 2);
        } else if (!powered) {
            level.setBlock(pos, POWERED.with(state, true), 2);
            if (!should) level.scheduleTick(pos, block, getDelay(state), VERY_HIGH);
        }
    }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return getSignal(state, level, pos, dir); }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) {
        if (!POWERED.is(state)) return 0;
        return facing(state) == dir ? getOutputSignal(level, pos, state) : 0;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (canSurvive(state, level, pos)) {
            checkTickOnNeighbor(level, pos, state);
        } else {
            level.dropResources(state, pos, INFO.hasBlockEntity(state) ? level.getBlockEntity(pos) : null, null, ItemStack.EMPTY);
            level.removeBlock(pos, false);
            for (int d : Dir.ALL) level.updateNeighborsAt(pos.relative(d), block);
        }
    }

    protected void checkTickOnNeighbor(BlockLevel level, Pos pos, int state) {
        if (isLocked(level, pos, state)) return;
        boolean powered = POWERED.is(state);
        boolean should = shouldTurnOn(level, pos, state);
        if (powered != should && !level.willTickThisTick(pos, block)) {
            int priority = HIGH;
            if (shouldPrioritize(level, pos, state)) priority = EXTREMELY_HIGH;
            else if (powered) priority = VERY_HIGH;
            level.scheduleTick(pos, block, getDelay(state), priority);
        }
    }

    public boolean isLocked(BlockLevel level, Pos pos, int state) { return false; }

    protected boolean shouldTurnOn(BlockLevel level, Pos pos, int state) { return getInputSignal(level, pos, state) > 0; }

    /** getInputSignal: 뒤 칸이 이쪽으로 내는 신호(레드스톤 가루면 그 세기와 큰 쪽). */
    protected int getInputSignal(BlockLevel level, Pos pos, int state) {
        int dir = facing(state);
        Pos p = pos.relative(dir);
        int i = level.getSignal(p, dir);
        if (i >= 15) return i;
        int s = level.getBlockState(p);
        return Math.max(i, B.is(s, REDSTONE_WIRE) ? WIRE_POWER.intValue(s) : 0);
    }

    /** getAlternateSignal: 양옆 제어 입력의 큰 쪽. */
    protected int getAlternateSignal(BlockLevel level, Pos pos, int state) {
        int dir = facing(state);
        int cw = Dir.clockWise(dir), ccw = Dir.counterClockWise(dir);
        boolean diodesOnly = sideInputDiodesOnly();
        return Math.max(getControlInputSignal(level, pos.relative(cw), cw, diodesOnly), getControlInputSignal(level, pos.relative(ccw), ccw, diodesOnly));
    }

    /** SignalGetter.getControlInputSignal. */
    static int getControlInputSignal(BlockLevel level, Pos pos, int dir, boolean diodesOnly) {
        int s = level.getBlockState(pos);
        if (diodesOnly) return isDiode(s) ? level.getDirectSignal(pos, dir) : 0;
        if (B.is(s, REDSTONE_BLOCK)) return 15;
        if (B.is(s, REDSTONE_WIRE)) return WIRE_POWER.intValue(s);
        return B.behavior(s).isSignalSource(s) ? level.getDirectSignal(pos, dir) : 0;
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        if (shouldTurnOn(level, pos, state)) level.scheduleTick(pos, block, 1);
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) { updateNeighborsInFront(level, pos, state); }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved && !B.is(newState, block)) {
            super.onRemove(state, level, pos, newState, moved);
            updateNeighborsInFront(level, pos, state);
        }
    }

    protected void updateNeighborsInFront(BlockLevel level, Pos pos, int state) {
        int dir = facing(state);
        Pos front = pos.relative(Dir.opposite(dir));
        level.neighborChanged(front, block, pos);
        level.updateNeighborsAtExceptFromFacing(front, block, dir);
    }

    protected boolean sideInputDiodesOnly() { return false; }

    protected int getOutputSignal(BlockLevel level, Pos pos, int state) { return 15; }

    public static boolean isDiode(int state) { return B.behavior(state) instanceof DiodeBlock; }

    /** shouldPrioritize: 앞 칸이 이쪽을 향하지 않은 다이오드인가. */
    public boolean shouldPrioritize(BlockLevel level, Pos pos, int state) {
        int dir = Dir.opposite(facing(state));
        int s = level.getBlockState(pos.relative(dir));
        return isDiode(s) && facing(s) != dir;
    }

    protected abstract int getDelay(int state);
}
