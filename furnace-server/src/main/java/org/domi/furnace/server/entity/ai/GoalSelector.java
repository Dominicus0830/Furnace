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

    public void addGoal(int priority, Goal goal) { availableGoals.add(new WrappedGoal(priority, goal)); }

    public void removeAllGoals(Predicate<Goal> filter) { availableGoals.removeIf(w -> filter.test(w.getGoal())); }

    /** 목표 틱 도중의 removeGoal(해골 덫이 자기를 뺀다): 멈추기만 하고 빼기는 돌기가 끝난 뒤(바닐라 fastutil 집합은 돌면서 빼도 이어 돈다). */
    private boolean ticking;
    private java.util.List<Goal> pendingRemoval;

    public void removeGoal(Goal goal) {
        for (WrappedGoal w : availableGoals) if (w.getGoal() == goal && w.isRunning()) w.stop();
        if (ticking) {
            if (pendingRemoval == null) pendingRemoval = new java.util.ArrayList<>(1);
            pendingRemoval.add(goal);
        } else {
            availableGoals.removeIf(w -> w.getGoal() == goal);
        }
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

    public void tick() {
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
        tickRunningGoals(true);
    }

    public void tickRunningGoals(boolean forceTickAllRunning) {
        ticking = true;
        try {
            for (WrappedGoal w : availableGoals) {
                if (w.isRunning() && (forceTickAllRunning || w.requiresUpdateEveryTick())) w.tick();
            }
        } finally {
            ticking = false;
            if (pendingRemoval != null) {
                for (Goal g : pendingRemoval) availableGoals.removeIf(w -> w.getGoal() == g);
                pendingRemoval = null;
            }
        }
    }

    public Set<WrappedGoal> getAvailableGoals() { return availableGoals; }

    public void disableControlFlag(Goal.Flag f) { disabledFlags.add(f); }

    public void enableControlFlag(Goal.Flag f) { disabledFlags.remove(f); }

    public void setControlFlag(Goal.Flag f, boolean enabled) {
        if (enabled) enableControlFlag(f); else disableControlFlag(f);
    }
}
