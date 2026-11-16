// 바닐라 GoalSelector: 우선순위 목표 선택. 틱마다 정리(끝낼 것) → 새 목표 시작(제어 칸을 더 높은 우선순위가 쥐게) → 돌고 있는 목표 틱.
package org.domi.furnace.server.entity.ai;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public class GoalSelector {
    private static final WrappedGoal NO_GOAL = new WrappedGoal(Integer.MAX_VALUE, new Goal() {
        @Override public boolean canUse() { return false; }
    }) {
        @Override public boolean isRunning() { return false; }
    };

    private final Map<Goal.Flag, WrappedGoal> lockedFlags = new EnumMap<>(Goal.Flag.class);
    private final Set<WrappedGoal> availableGoals = new LinkedHashSet<>();
    private final EnumSet<Goal.Flag> disabledFlags = EnumSet.noneOf(Goal.Flag.class);

    public void addGoal(int priority, Goal goal) {
        WrappedGoal w = new WrappedGoal(priority, goal);
        if (ticking) defer(() -> availableGoals.add(w));
        else availableGoals.add(w);
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        if (ticking) defer(() -> availableGoals.removeIf(w -> filter.test(w.getGoal())));
        else availableGoals.removeIf(w -> filter.test(w.getGoal()));
    }

    /**
     * 목표를 돌던 도중의 넣기·빼기(해골 덫이 자기를 빼고, 플러그인 목표가 다른 목표를 바꾼다): 빼는 목표는 바로 멈추고, 집합 변경은 돌기가 끝난 뒤
     * 부른 순서대로(바닐라 fastutil 집합은 돌면서 빼도 이어 돈다 — LinkedHashSet 은 CME 라 미룬다).
     */
    private boolean ticking;
    private java.util.List<Runnable> pendingOps;

    private void defer(Runnable op) {
        if (pendingOps == null) pendingOps = new java.util.ArrayList<>(1);
        pendingOps.add(op);
    }

    private void flushPending() {
        java.util.List<Runnable> ops = pendingOps;
        if (ops == null) return;
        pendingOps = null;
        for (Runnable op : ops) op.run();
    }

    public void removeGoal(Goal goal) {
        for (WrappedGoal w : availableGoals) if (w.getGoal() == goal && w.isRunning()) w.stop();
        if (ticking) defer(() -> availableGoals.removeIf(w -> w.getGoal() == goal));
        else availableGoals.removeIf(w -> w.getGoal() == goal);
    }

    /** 조건에 맞는 목표를 모두 멈추고 뺀다(플러그인 API). 뺀 개수(돌기 도중이면 곧 빠질 개수). */
    public int removeGoalsMatching(Predicate<Goal> filter) {
        java.util.List<Goal> hit = new java.util.ArrayList<>();
        for (WrappedGoal w : availableGoals) {
            if (!filter.test(w.getGoal())) continue;
            if (w.isRunning()) w.stop();
            hit.add(w.getGoal());
        }
        if (hit.isEmpty()) return 0;
        java.util.Set<Goal> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        set.addAll(hit);
        if (ticking) defer(() -> availableGoals.removeIf(w -> set.contains(w.getGoal())));
        else availableGoals.removeIf(w -> set.contains(w.getGoal()));
        return hit.size();
    }

    private static boolean goalContainsAnyFlags(WrappedGoal goal, EnumSet<Goal.Flag> flags) {
        if (flags.isEmpty()) return false;   // 막힌 칸이 없는 경우가 대부분 — 목표의 칸 집합을 돌지 않는다
        for (Goal.Flag f : goal.getFlags()) if (flags.contains(f)) return true;
        return false;
    }

    private static boolean goalCanBeReplacedForAllFlags(WrappedGoal goal, Map<Goal.Flag, WrappedGoal> locked) {
        for (Goal.Flag f : goal.getFlags()) if (!locked.getOrDefault(f, NO_GOAL).canBeReplacedBy(goal)) return false;
        return true;
    }

    /** Paper: 활성 범위 밖 틱 수(Mob.inactiveTick 이 3번에 한 번만 tick 을 부른다). */
    private int curRate;

    /** Paper GoalSelector.inactiveTick. */
    public boolean inactiveTick() {
        curRate++;
        return curRate % 3 == 0;
    }

    /** Paper GoalSelector.hasTasks: 돌고 있는 목표가 있는가. */
    public boolean hasTasks() {
        for (WrappedGoal w : availableGoals) if (w.isRunning()) return true;
        return false;
    }

    public void tick() {
        if (ticking) return;   // 목표 콜백 안에서 다시 부르면 무시(바닐라 코드는 그러지 않는다)
        ticking = true;
        try {
            selectGoals();
            tickGoals(true);
        } finally {
            ticking = false;
            flushPending();
        }
    }

    private void selectGoals() {
        for (WrappedGoal w : availableGoals) {
            if (w.isRunning() && (goalContainsAnyFlags(w, disabledFlags) || !w.canContinueToUse())) w.stop();
        }
        lockedFlags.entrySet().removeIf(e -> !e.getValue().isRunning());
        for (WrappedGoal w : availableGoals) {
            if (!w.isRunning() && !goalContainsAnyFlags(w, disabledFlags) && goalCanBeReplacedForAllFlags(w, lockedFlags) && w.canUse()) {
                for (Goal.Flag f : w.getFlags()) {
                    lockedFlags.getOrDefault(f, NO_GOAL).stop();
                    lockedFlags.put(f, w);
                }
                w.start();
            }
        }
    }

    public void tickRunningGoals(boolean forceTickAllRunning) {
        if (ticking) return;
        ticking = true;
        try {
            tickGoals(forceTickAllRunning);
        } finally {
            ticking = false;
            flushPending();
        }
    }

    private void tickGoals(boolean forceTickAllRunning) {
        for (WrappedGoal w : availableGoals) {
            if (w.isRunning() && (forceTickAllRunning || w.requiresUpdateEveryTick())) w.tick();
        }
    }

    public Set<WrappedGoal> getAvailableGoals() { return availableGoals; }

    public void disableControlFlag(Goal.Flag f) { disabledFlags.add(f); }

    public void enableControlFlag(Goal.Flag f) { disabledFlags.remove(f); }

    public void setControlFlag(Goal.Flag f, boolean enabled) {
        if (enabled) enableControlFlag(f); else disableControlFlag(f);
    }
}
