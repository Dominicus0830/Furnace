package net.minecraft.world.entity.ai.goal;

import java.util.Set;
import java.util.function.Predicate;
import org.domi.furnace.compat.nms.NmsGoals;
import org.domi.furnace.compat.nms.UnsupportedNmsCall;

/**
 * 몹 목표 선택기 shim(몹 목표 설계 M1). 서버 몹의 행동·대상 선택기를 본다 — 모든 조작은 그 몹의 주인 스레드에서(규칙은 {@link NmsGoals}).
 * 바닐라처럼 {@code removeGoal} 만 돌던 목표를 멈추고 빼며, {@code removeAllGoals}·집합 보기의 빼기는 집합에서만 뺀다(Paper 1.21.1 jar 로 확인).
 * 선택기를 새로 만드는 Paper 생성자는 껍데기다(몹에 붙지 않은 선택기는 서버에 닿지 않는다).
 */
class GoalSelector {

    private final net.minecraft.world.entity.Mob furnaceMob;
    private final boolean furnaceTargets;
    private final org.domi.furnace.compat.nms.NmsGoalSet furnaceSet;

    protected GoalSelector(org.domi.furnace.compat.nms.ShimCtor tag) {
        NmsGoals.SelectorOf of = NmsGoals.selectorOf(tag);
        this.furnaceMob = of.mob();
        this.furnaceTargets = of.targets();
        this.furnaceSet = new org.domi.furnace.compat.nms.NmsGoalSet(furnaceMob, furnaceTargets);
    }

    public void addGoal(int priority, Goal goal) {
        NmsGoals.Backing b = goal.furnaceBacking();
        if (b == null) throw new UnsupportedNmsCall("GoalSelector#addGoal", "goal " + goal.getClass().getName() + ": " + NmsGoals.M2_HINT);
        NmsGoals.on(furnaceMob, "GoalSelector#addGoal", g -> {
            g.addGoal(furnaceTargets, priority, b.server());
            return null;
        });
    }

    public void removeGoal(Goal goal) {
        NmsGoals.Backing b = goal.furnaceBacking();
        if (b == null) return;   // 서버에 없는 목표 — 바닐라도 맞는 것이 없으면 아무것도 안 한다
        NmsGoals.on(furnaceMob, "GoalSelector#removeGoal", g -> {
            g.removeGoal(furnaceTargets, b.server());
            return null;
        });
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        NmsGoals.on(furnaceMob, "GoalSelector#removeAllGoals", g -> {
            g.removeFromSet(furnaceTargets, w -> filter.test(NmsGoals.goalView(furnaceMob, g, g.goal(w))));
            return null;
        });
    }

    public Set<WrappedGoal> getAvailableGoals() { return furnaceSet; }

    public void tick() {
        NmsGoals.on(furnaceMob, "GoalSelector#tick", g -> {
            g.tick(furnaceTargets);
            return null;
        });
    }

    public void tickRunningGoals(boolean tickAllRunning) {
        NmsGoals.on(furnaceMob, "GoalSelector#tickRunningGoals", g -> {
            g.tickRunningGoals(furnaceTargets, tickAllRunning);
            return null;
        });
    }

    public void disableControlFlag(Goal.Flag flag) { setControlFlag(flag, false); }

    public void enableControlFlag(Goal.Flag flag) { setControlFlag(flag, true); }

    /** Paper 의 UNKNOWN_BEHAVIOR 는 서버 목표가 쓰지 않는 깃발이라 막아도 바뀌는 것이 없다. */
    public void setControlFlag(Goal.Flag flag, boolean enabled) {
        int c = NmsGoals.control(flag);
        if (c < 0) return;
        NmsGoals.on(furnaceMob, "GoalSelector#setControlFlag", g -> {
            g.setControlFlag(furnaceTargets, c, enabled);
            return null;
        });
    }

    public boolean inactiveTick() { return NmsGoals.on(furnaceMob, "GoalSelector#inactiveTick", g -> g.inactiveTick(furnaceTargets)); }

    public boolean hasTasks() { return NmsGoals.on(furnaceMob, "GoalSelector#hasTasks", g -> g.hasTasks(furnaceTargets)); }
}
