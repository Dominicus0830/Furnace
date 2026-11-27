package net.minecraft.world.entity.ai.goal;

import java.util.Set;
import java.util.function.Predicate;
import org.domi.furnace.api.internal.ChunkInternals.EntityOp;
import org.domi.furnace.compat.nms.NmsGoals;

/**
 * 몹 목표 선택기 shim(몹 목표 설계 M1). 서버 몹의 행동·대상 선택기를 본다 — 모든 조작은 그 몹의 주인 스레드에서(규칙은 {@link NmsGoals}).
 * 바닐라처럼 {@code removeGoal} 만 돌던 목표를 멈추고 빼며, {@code removeAllGoals}·집합 보기의 빼기는 집합에서만 뺀다(Paper 1.21.1 jar 로 확인).
 * 아직 월드에 없는 몹(M3)은 그 샌드박스 몹의 선택기를 보고, 바꾸는 조작은 기록해 넣을 때 재생한다(판정 함수는 잠금 밖에서 한 번만 돈다).
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

    /** 서버 목표를 보는 shim 은 그 서버 목표를, 플러그인이 상속한 목표는 서버 어댑터(몹 목표 M2a)를 넣는다. */
    public void addGoal(int priority, Goal goal) {
        String what = "GoalSelector#addGoal";
        NmsGoals.onMob(furnaceMob, what, g -> {
            g.addGoal(furnaceTargets, priority, NmsGoals.serverGoal(furnaceMob, g, goal));
            return null;
        }, box -> box.locked(what, g -> {
            box.record(new EntityOp.GoalAdd(furnaceTargets, priority, NmsGoals.sandboxGoalRef(furnaceMob, box, goal)));
            return null;
        }));
    }

    /** 바닐라처럼 같은 목표 객체를 찾는다(플러그인 목표는 그것을 감싼 서버 어댑터). 없으면 아무것도 안 한다. */
    public void removeGoal(Goal goal) {
        String what = "GoalSelector#removeGoal";
        NmsGoals.Backing b = goal.furnaceBacking();
        NmsGoals.onMob(furnaceMob, what, g -> {
            Object key = b == null ? goal : b.worldServer();
            if (key != null) g.removeGoal(furnaceTargets, key);
            return null;
        }, box -> box.locked(what, g -> {
            Object ref = NmsGoals.sandboxRemoveRef(box, goal);
            if (ref != null) box.record(new EntityOp.GoalRemove(furnaceTargets, ref));
            return null;
        }));
    }

    public void removeAllGoals(Predicate<Goal> filter) {
        String what = "GoalSelector#removeAllGoals";
        NmsGoals.onMob(furnaceMob, what, g -> {
            g.removeFromSet(furnaceTargets, w -> filter.test(NmsGoals.goalView(furnaceMob, g, g.goal(w))));
            return null;
        }, box -> {
            furnaceSet.removeInSandbox(box, w -> filter.test(w.getGoal()));
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
        String what = "GoalSelector#setControlFlag";
        NmsGoals.onMob(furnaceMob, what, g -> {
            g.setControlFlag(furnaceTargets, c, enabled);
            return null;
        }, box -> box.locked(what, g -> {
            box.record(new EntityOp.GoalControlFlag(furnaceTargets, c, enabled));
            return null;
        }));
    }

    public boolean inactiveTick() { return NmsGoals.on(furnaceMob, "GoalSelector#inactiveTick", g -> g.inactiveTick(furnaceTargets)); }

    public boolean hasTasks() {
        String what = "GoalSelector#hasTasks";
        return NmsGoals.onMob(furnaceMob, what, g -> g.hasTasks(furnaceTargets), box -> box.locked(what, g -> g.hasTasks(furnaceTargets)));
    }
}
