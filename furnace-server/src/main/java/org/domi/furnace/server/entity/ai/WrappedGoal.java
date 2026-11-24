// 바닐라 WrappedGoal: 우선순위가 붙은 목표와 실행 중 여부.
package org.domi.furnace.server.entity.ai;

import java.util.EnumSet;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.ai.goal.WrappedGoal")
public class WrappedGoal extends Goal {
    private final Goal goal;
    private final int priority;
    private boolean isRunning;
    /** 호환 계층의 NMS 모양 객체(몹 목표 설계 M1). 주인 스레드에서만 읽고 쓴다. */
    public Object compatView;
    /** 돌기 도중 빼기로 미뤄진 목표(그 패스의 시작·틱에서 건너뛴다 — 바닐라 집합은 바로 빠진다). */
    boolean leaving;

    public WrappedGoal(int priority, Goal goal) {
        this.priority = priority;
        this.goal = goal;
    }

    public boolean canBeReplacedBy(WrappedGoal other) {
        return isInterruptable() && other.getPriority() < getPriority();
    }

    @Override public boolean canUse() { return goal.canUse(); }

    @Override public boolean canContinueToUse() { return goal.canContinueToUse(); }

    @Override public boolean isInterruptable() { return goal.isInterruptable(); }

    @Override
    public void start() {
        if (!isRunning) {
            isRunning = true;
            goal.start();
        }
    }

    @Override
    public void stop() {
        if (isRunning) {
            isRunning = false;
            goal.stop();
        }
    }

    @Override public boolean requiresUpdateEveryTick() { return goal.requiresUpdateEveryTick(); }

    @Override protected int adjustedTickDelay(int n) { return goal.adjustedTickDelay(n); }

    @Override public void tick() { goal.tick(); }

    @Override public void setFlags(EnumSet<Flag> f) { goal.setFlags(f); }

    @Override public EnumSet<Flag> getFlags() { return goal.getFlags(); }

    public boolean isRunning() { return isRunning; }

    public int getPriority() { return priority; }

    public Goal getGoal() { return goal; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && getClass() == o.getClass() && goal.equals(((WrappedGoal) o).goal);
    }

    @Override public int hashCode() { return goal.hashCode(); }

    @Override public String toString() { return goal.toString(); }
}
