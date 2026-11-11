// 바닐라 RedstoneTorchBlock: 아래 칸이 신호를 받으면 꺼진다(2틱 뒤). 60틱 안에 8번 꺼지면 타 버려 160틱 쉰다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class RedstoneTorchBlock extends BaseTorchBlock {

    static final Prop LIT = B.prop("lit");
    private static final int RECENT_TOGGLE_TIMER = 60, MAX_RECENT_TOGGLES = 8, RESTART_DELAY = 160, TOGGLE_DELAY = 2;
    // 최근 꺼짐 기록(바닐라 정적 RECENT_TOGGLES — 레벨 키 WeakHashMap)은 레벨이 들고 있다(BlockLevel.redstoneTorchToggles).
    // region 스레드 여럿이 정적 표 하나를 잠금 없이 쓰면 표가 깨지고(무한 반복), 레벨 객체 키는 region 합치기·나누기 때 기록을 잃는다.

    public RedstoneTorchBlock(int block) { super(block); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        for (int d : Dir.ALL) level.updateNeighborsAt(pos.relative(d), block);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!moved) {
            for (int d : Dir.ALL) level.updateNeighborsAt(pos.relative(d), block);
        }
    }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return LIT.is(state) && dir != Dir.UP ? 15 : 0; }

    /** hasNeighborSignal: 붙은 칸(아래)이 신호를 받는가. */
    protected boolean hasNeighborSignal(BlockLevel level, Pos pos, int state) { return level.hasSignal(pos.below(), Dir.DOWN); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        boolean signal = hasNeighborSignal(level, pos, state);
        level.redstoneTorchToggles().expire(level.gameTime(), RECENT_TOGGLE_TIMER);
        if (LIT.is(state)) {
            if (signal) {
                level.setBlock(pos, LIT.with(state, false), 3);
                if (isToggledTooFrequently(level, pos, true)) {
                    level.levelEvent(null, 1502, pos, 0);
                    level.scheduleTick(pos, B.block(level.getBlockState(pos)), RESTART_DELAY);
                }
            }
        } else if (!signal && !isToggledTooFrequently(level, pos, false)) {
            level.setBlock(pos, LIT.with(state, true), 3);
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (LIT.is(state) == hasNeighborSignal(level, pos, state) && !level.willTickThisTick(pos, block)) {
            level.scheduleTick(pos, block, TOGGLE_DELAY);
        }
    }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) {
        return dir == Dir.DOWN ? B.behavior(state).getSignal(state, level, pos, dir) : 0;
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    private static boolean isToggledTooFrequently(BlockLevel level, Pos pos, boolean add) {
        var toggles = level.redstoneTorchToggles();
        if (add) toggles.add(pos, level.gameTime());
        return toggles.count(pos) >= MAX_RECENT_TOGGLES;
    }
}
