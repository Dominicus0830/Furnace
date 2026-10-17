// 바닐라 ComparatorBlock: 레드스톤 비교기(mode compare/subtract). 뒤 입력(아날로그 신호 포함)과 옆 입력을 비교해 출력을 블록 엔티티에 둔다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class ComparatorBlock extends DiodeBlock {

    static final Prop MODE = B.prop("mode");

    public ComparatorBlock(int block) { super(block); }

    private static boolean subtract(int state) { return "subtract".equals(MODE.valueName(state)); }

    @Override
    protected int getDelay(int state) { return 2; }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurviveOn(level, neighborPos, neighborState) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    /** 블록 엔티티(ComparatorBlockEntity)의 출력. TODO(블록 엔티티): 블록 엔티티가 들어오면 거기서 읽고 쓴다 — 지금은 없음(0). */
    private static int storedOutput(BlockLevel level, Pos pos) { return 0; }

    private static void storeOutput(BlockLevel level, Pos pos, int output) {}

    @Override
    protected int getOutputSignal(BlockLevel level, Pos pos, int state) { return storedOutput(level, pos); }

    private int calculateOutputSignal(BlockLevel level, Pos pos, int state) {
        int in = getInputSignal(level, pos, state);
        if (in == 0) return 0;
        int alt = getAlternateSignal(level, pos, state);
        if (alt > in) return 0;
        return subtract(state) ? in - alt : in;
    }

    @Override
    protected boolean shouldTurnOn(BlockLevel level, Pos pos, int state) {
        int in = getInputSignal(level, pos, state);
        if (in == 0) return false;
        int alt = getAlternateSignal(level, pos, state);
        return in > alt || in == alt && !subtract(state);
    }

    @Override
    protected int getInputSignal(BlockLevel level, Pos pos, int state) {
        int i = super.getInputSignal(level, pos, state);
        int dir = facing(state);
        Pos p = pos.relative(dir);
        int s = level.getBlockState(p);
        if (B.behavior(s).hasAnalogOutputSignal(s)) {
            i = B.behavior(s).getAnalogOutputSignal(s, level, p);
        } else if (i < 15 && B.behavior(s).isRedstoneConductor(s, level, p)) {
            p = p.relative(dir);
            s = level.getBlockState(p);
            // TODO(엔티티): 그 칸에 이쪽을 향한 아이템 액자가 하나뿐이면 그 아날로그 신호(getItemFrame).
            int k = B.behavior(s).hasAnalogOutputSignal(s) ? B.behavior(s).getAnalogOutputSignal(s, level, p) : Integer.MIN_VALUE;
            if (k != Integer.MIN_VALUE) i = k;
        }
        return i;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!player.mayBuild()) return InteractionResult.PASS;
        int s = MODE.cycle(state);
        float pitch = subtract(s) ? 0.55F : 0.5F;
        level.playSound(player, pos, "minecraft:block.comparator.click", "blocks", 0.3F, pitch);
        level.setBlock(pos, s, 2);
        refreshOutputState(level, pos, s);
        return InteractionResult.sidedSuccess(false);
    }

    @Override
    protected void checkTickOnNeighbor(BlockLevel level, Pos pos, int state) {
        if (level.willTickThisTick(pos, block)) return;
        int out = calculateOutputSignal(level, pos, state);
        int stored = storedOutput(level, pos);
        if (out != stored || POWERED.is(state) != shouldTurnOn(level, pos, state)) {
            level.scheduleTick(pos, block, 2, shouldPrioritize(level, pos, state) ? HIGH : NORMAL);
        }
    }

    private void refreshOutputState(BlockLevel level, Pos pos, int state) {
        int out = calculateOutputSignal(level, pos, state);
        int old = storedOutput(level, pos);
        storeOutput(level, pos, out);
        if (old != out || !subtract(state)) {
            boolean should = shouldTurnOn(level, pos, state);
            boolean powered = POWERED.is(state);
            if (powered && !should) level.setBlock(pos, POWERED.with(state, false), 2);
            else if (!powered && should) level.setBlock(pos, POWERED.with(state, true), 2);
            updateNeighborsInFront(level, pos, state);
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { refreshOutputState(level, pos, state); }
}
