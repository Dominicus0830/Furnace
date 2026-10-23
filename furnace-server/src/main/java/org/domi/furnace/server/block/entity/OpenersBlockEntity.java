// 여는 사람 수를 세는 블록 엔티티의 바탕(바닐라 ContainerOpenersCounter 를 가진 상자·통·엔더 상자·셜커 상자): 열린 수가 0↔1 일 때 소리·열림 상태,
// 바뀔 때마다 block_event, 열린 동안 5틱마다 실제로 연 플레이어를 다시 센다. startOpen/stopOpen/recheckOpen 은 그 블록의 region 스레드에서.
package org.domi.furnace.server.block.entity;

import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public abstract class OpenersBlockEntity extends BlockEntity {

    /** 지금 이 용기를 열어 둔 사람 수(ContainerOpenersCounter). 틱 스레드에서만 바꾼다. */
    public volatile int openCount;

    protected OpenersBlockEntity(String type, Pos pos, int blockState) { super(type, pos, blockState); }

    /** 열린 창을 가진 플레이어가 있는 동안 5틱마다 다시 센다. */
    private static final int RECHECK_TICK_DELAY = 5;
    /** 플레이어 블록 상호작용 거리(기본 4.5) — 다시 셀 범위의 바탕. */
    private static final double INTERACTION_RANGE = 4.5;

    /** 이 종류가 여는 사람 수를 세는가(상자·통). 셜커 상자는 자기 방식으로 센다. */
    protected boolean usesOpenersCounter() { return false; }

    /** 열린 동안 5틱마다 실제 수를 다시 세는가(상자·통 — 셜커 상자는 아니다). */
    protected boolean rechecksOpeners() { return true; }

    /** 열린 수가 0 → 1 이 될 때(소리·열림 상태). */
    protected void onOpen(BlockLevel level, Pos pos, int state) {}

    /** 열린 수가 1 → 0 이 될 때. */
    protected void onClose(BlockLevel level, Pos pos, int state) {}

    /** 열린 수가 바뀔 때마다(old, now). */
    protected void openerCountChanged(BlockLevel level, Pos pos, int state, int old, int now) {}

    /** 플레이어가 이 용기를 열었다 — 그 블록의 region 스레드에서. (바닐라 startOpen → ContainerOpenersCounter.incrementOpeners) */
    public final void startOpen(BlockLevel level) {
        if (!usesOpenersCounter()) return;
        int state = level.getBlockState(pos);
        int old = openCount++;
        if (old == 0) {
            onOpen(level, pos, state);
            level.gameEvent("minecraft:container_open", pos, null, state);   // TODO(엔티티): 연 플레이어를 source 로
            if (rechecksOpeners()) scheduleRecheck(level, pos, state);
        }
        openerCountChanged(level, pos, state, old, openCount);
    }

    /** 플레이어가 이 용기를 닫았다. (decrementOpeners) */
    public final void stopOpen(BlockLevel level) {
        if (!usesOpenersCounter()) return;
        int state = level.getBlockState(pos);
        int old = openCount--;
        if (openCount == 0) {
            onClose(level, pos, state);
            level.gameEvent("minecraft:container_close", pos, null, state);
        }
        openerCountChanged(level, pos, state, old, openCount);
    }

    /** 블록 tick 이 부른다: 이 용기를 실제로 열어 둔 플레이어 수로 바로잡는다(접속이 끊긴 사람 등). (recheckOpeners) */
    public final void recheckOpen(BlockLevel level) {
        if (!usesOpenersCounter() || !rechecksOpeners()) return;
        int state = level.getBlockState(pos);
        int count = level.countContainerOpeners(pos, INTERACTION_RANGE + 4.0, this);
        int old = openCount;
        if (old != count) {
            boolean now = count != 0, was = old != 0;
            if (now && !was) {
                onOpen(level, pos, state);
                level.gameEvent("minecraft:container_open", pos, null, state);
            } else if (!now) {
                onClose(level, pos, state);
                level.gameEvent("minecraft:container_close", pos, null, state);
            }
            openCount = count;
        }
        openerCountChanged(level, pos, state, old, count);
        if (count > 0) scheduleRecheck(level, pos, state);
    }

    private static void scheduleRecheck(BlockLevel level, Pos pos, int state) {
        level.scheduleTick(pos, org.domi.furnace.server.block.B.block(state), RECHECK_TICK_DELAY);
    }
}
