// 바닐라 Goal: 몹이 하는 일 하나. 시작 조건(canUse)·계속 조건(canContinueToUse)·시작/끝/틱, 쓰는 제어 칸(Flag).
package org.domi.furnace.server.entity.ai;

import java.util.EnumSet;

public abstract class Goal {
    public enum Flag { MOVE, LOOK, JUMP, TARGET }

    private final EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);

    public abstract boolean canUse();

    public boolean canContinueToUse() { return canUse(); }

    public boolean isInterruptable() { return true; }

    public void start() {}

    public void stop() {}

    public boolean requiresUpdateEveryTick() { return false; }

    public void tick() {}

    public void setFlags(EnumSet<Flag> f) {
        flags.clear();
        flags.addAll(f);
    }

    public EnumSet<Flag> getFlags() { return flags; }

    /** adjustedTickDelay: 매 틱 도는 목표가 아니면 절반 주기(reducedTickDelay = positiveCeilDiv(n, 2)). */
    protected int adjustedTickDelay(int n) { return requiresUpdateEveryTick() ? n : reducedTickDelay(n); }

    protected static int reducedTickDelay(int n) { return Math.floorDiv(n + 1, 2); }

    @Override
    public String toString() { return getClass().getSimpleName(); }
}
