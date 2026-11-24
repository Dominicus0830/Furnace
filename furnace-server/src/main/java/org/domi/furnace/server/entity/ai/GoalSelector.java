// 바닐라 GoalSelector: 우선순위 목표 선택. 틱마다 정리(끝낼 것) → 새 목표 시작(제어 칸을 더 높은 우선순위가 쥐게) → 돌고 있는 목표 틱.
package org.domi.furnace.server.entity.ai;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

@org.domi.furnace.server.VanillaPort("net.minecraft.world.entity.ai.goal.GoalSelector")
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
        addToSet(new WrappedGoal(priority, goal));
    }

    /** 바닐라 removeAllGoals: 집합에서만 뺀다(돌던 목표도 멈추지 않는다 — Paper 1.21.1 과 같음). */
    public void removeAllGoals(Predicate<Goal> filter) {
        removeFromSet(w -> filter.test(w.getGoal()));
    }

    /**
     * 집합에서만 뺀다(바닐라에서 {@code getAvailableGoals()} 를 바로 고친 것과 같다): 돌던 목표는 멈추지 않고 잠근 칸도 그대로 남는다.
     * 돌기 도중이면 맞는 목표에 빠질 예정 표시를 해 그 패스에서 건너뛰고(집합 변경은 돌기가 끝난 뒤), 같은 패스에서 미뤄 둔 넣기도 판정해 취소한다.
     * 판정 함수가 예외를 던지면 그때까지 맞은 것만 빠진다(바닐라 removeIf 와 같다).
     */
    public void removeFromSet(Predicate<WrappedGoal> filter) {
        if (!ticking) {
            availableGoals.removeIf(filter);
            return;
        }
        java.util.List<WrappedGoal> victims = new java.util.ArrayList<>();
        pending().add(victims);   // 판정 도중 예외여도 표시한 것은 끝에 빠지고 표시도 꺼진다
        for (WrappedGoal w : availableGoals) {
            if (!w.leaving && filter.test(w)) {
                w.leaving = true;
                victims.add(w);
            }
        }
        java.util.List<Object> ops = pendingOps;
        for (int i = 0; i < ops.size(); i++) {
            if (ops.get(i) instanceof WrappedGoal added && filter.test(added)) ops.set(i, null);
        }
    }

    /**
     * 집합에 넣는다(바닐라 {@code getAvailableGoals().add} — 같은 목표가 이미 있으면 false). 돌기 도중이면 집합 변경은 끝난 뒤에. 빠질 예정인 같은 목표를
     * 다시 넣으면 표시를 끄고(그 패스에서 다시 보인다) 끝에서 빼고 넣어 바닐라처럼 맨 뒤로 간다.
     */
    public boolean addToSet(WrappedGoal w) {
        if (!ticking) return availableGoals.add(w);
        if (visibleContains(w)) return false;
        for (WrappedGoal x : availableGoals) if (x.leaving && x.equals(w)) x.leaving = false;
        pending().add(w);
        return true;
    }

    /** 바닐라 집합에 지금 보일 목표들(빠질 예정은 빼고, 돌기 도중 미뤄 둔 넣기는 더해). */
    public java.util.List<WrappedGoal> visibleGoals() {
        java.util.List<WrappedGoal> out = new java.util.ArrayList<>(availableGoals.size());
        for (WrappedGoal w : availableGoals) if (!w.leaving) out.add(w);
        if (pendingOps != null) for (Object op : pendingOps) if (op instanceof WrappedGoal w) out.add(w);
        return out;
    }

    public int visibleSize() {
        if (pendingOps == null) return availableGoals.size();
        int n = 0;
        for (WrappedGoal w : availableGoals) if (!w.leaving) n++;
        for (Object op : pendingOps) if (op instanceof WrappedGoal) n++;
        return n;
    }

    public boolean visibleContains(WrappedGoal w) {
        if (pendingOps == null) return availableGoals.contains(w);
        for (WrappedGoal x : availableGoals) if (!x.leaving && x.equals(w)) return true;
        for (Object op : pendingOps) if (op instanceof WrappedGoal x && x.equals(w)) return true;
        return false;
    }

    /**
     * 목표를 돌던 도중의 넣기·빼기(해골 덫이 자기를 빼고, 플러그인 목표가 다른 목표를 바꾼다): 집합 변경은 돌기가 끝난 뒤 부른 순서대로
     * (바닐라 fastutil 집합은 돌면서 빼도 이어 돈다 — LinkedHashSet 은 CME 라 미룬다). 원소는 넣기({@link WrappedGoal}), 빼기(목표 목록),
     * 취소된 넣기(null).
     */
    private boolean ticking;
    private java.util.List<Object> pendingOps;

    private java.util.List<Object> pending() {
        if (pendingOps == null) pendingOps = new java.util.ArrayList<>(2);
        return pendingOps;
    }

    private void flushPending() {
        java.util.List<Object> ops = pendingOps;
        if (ops == null) return;
        pendingOps = null;
        for (Object op : ops) {
            if (op instanceof WrappedGoal w) {
                availableGoals.add(w);
            } else if (op instanceof java.util.List<?> victims) {
                for (Object v : victims) {
                    WrappedGoal w = (WrappedGoal) v;
                    availableGoals.remove(w);
                    w.leaving = false;
                }
            }
        }
    }

    public void removeGoal(Goal goal) {
        for (WrappedGoal w : availableGoals) if (!w.leaving && w.getGoal() == goal && w.isRunning()) w.stop();
        removeFromSet(w -> w.getGoal() == goal);
    }

    /** 조건에 맞는 목표를 모두 멈추고 뺀다(플러그인 API). 뺀 개수(돌기 도중이면 곧 빠질 개수). */
    public int removeGoalsMatching(Predicate<Goal> filter) {
        java.util.List<Goal> hit = new java.util.ArrayList<>();
        for (WrappedGoal w : availableGoals) {
            if (w.leaving || !filter.test(w.getGoal())) continue;
            if (w.isRunning()) w.stop();
            hit.add(w.getGoal());
        }
        if (hit.isEmpty()) return 0;
        java.util.Set<Goal> set = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        set.addAll(hit);
        removeFromSet(w -> set.contains(w.getGoal()));
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
        for (WrappedGoal w : availableGoals) if (!w.leaving && w.isRunning()) return true;
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
            if (!w.leaving && w.isRunning() && (goalContainsAnyFlags(w, disabledFlags) || !w.canContinueToUse())) w.stop();
        }
        lockedFlags.entrySet().removeIf(e -> !e.getValue().isRunning());
        for (WrappedGoal w : availableGoals) {
            if (!w.leaving && !w.isRunning() && !goalContainsAnyFlags(w, disabledFlags) && goalCanBeReplacedForAllFlags(w, lockedFlags) && w.canUse()) {
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
            if (!w.leaving && w.isRunning() && (forceTickAllRunning || w.requiresUpdateEveryTick())) w.tick();
        }
    }

    public Set<WrappedGoal> getAvailableGoals() { return availableGoals; }

    public void disableControlFlag(Goal.Flag f) { disabledFlags.add(f); }

    public void enableControlFlag(Goal.Flag f) { disabledFlags.remove(f); }

    public void setControlFlag(Goal.Flag f, boolean enabled) {
        if (enabled) enableControlFlag(f); else disableControlFlag(f);
    }
}
