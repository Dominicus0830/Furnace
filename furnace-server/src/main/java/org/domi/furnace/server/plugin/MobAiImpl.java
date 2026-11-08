// API MobAi: 서버 몹을 직접 잡고 소유 region 스레드에서만 다룬다. 호출마다 소유 스레드인지 확인한다(얇은 포장이라 필요할 때마다 만든다).
package org.domi.furnace.server.plugin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.entity.ai.GoalControl;
import org.domi.furnace.api.entity.ai.GoalInfo;
import org.domi.furnace.api.entity.ai.GoalKey;
import org.domi.furnace.api.entity.ai.GoalSet;
import org.domi.furnace.api.entity.ai.MobAi;
import org.domi.furnace.api.entity.ai.MobGoal;
import org.domi.furnace.api.entity.ai.PathNodeType;
import org.domi.furnace.api.entity.ai.PathTypeHook;
import org.domi.furnace.api.entity.ai.VanillaGoal;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.world.Location;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.GoalSelector;
import org.domi.furnace.server.entity.ai.WrappedGoal;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.slf4j.LoggerFactory;

final class MobAiImpl implements MobAi {

    private final PluginHost host;
    private final Mob mob;

    MobAiImpl(PluginHost host, Mob mob) {
        this.host = host;
        this.mob = mob;
    }

    private void check() {
        if (!org.domi.furnace.server.world.RegionBorrow.claimEntity(mob) && !mob.level().isOwnerThread()) {
            throw new ThreadOwnershipException("MobAi(" + mob.uuid() + ") 는 그 몹을 가진 region 스레드에서만 쓸 수 있다 (현재 "
                    + Thread.currentThread().getName() + ") — Mob.editAi 를 쓴다");
        }
    }

    GoalSelector selector(GoalSet set) { return set == GoalSet.TARGETS ? mob.targetSelector() : mob.goalSelector(); }

    @Override
    public UUID uniqueId() { return mob.uuid(); }

    @Override
    public String type() { return mob.type().name(); }

    @Override
    public Location location() {
        check();
        return new Location(mob.dimensionKey(), mob.x(), mob.y(), mob.z(), mob.yRot(), mob.xRot());
    }

    @Override
    public boolean isValid() {
        check();
        return !mob.isRemoved();
    }

    @Override
    public boolean isNoAi() {
        check();
        return mob.isNoAi();
    }

    @Override
    public boolean usesBrain() { return mob.getBrain() != null; }

    @Override
    public Optional<org.domi.furnace.api.entity.ai.brain.MobBrain> brain() {
        check();
        return mob.getBrain() == null ? Optional.empty() : Optional.of(new MobBrainImpl(host, mob));
    }

    @Override
    public org.domi.furnace.api.entity.Mob snapshot() {
        check();
        return (org.domi.furnace.api.entity.Mob) EntityHandle.of(host, mob);
    }

    // ------------------------------------------------------------------ 목표

    @Override
    public List<GoalInfo> goals(GoalSet set) {
        check();
        List<GoalInfo> out = new ArrayList<>();
        for (WrappedGoal w : selector(set).getAvailableGoals()) {
            Goal g = w.getGoal();
            EnumSet<GoalControl> controls = EnumSet.noneOf(GoalControl.class);
            for (Goal.Flag f : g.getFlags()) controls.add(GoalControl.valueOf(f.name()));
            out.add(new GoalInfo(GoalKeys.of(g), w.getPriority(), controls, w.isRunning(), g instanceof PluginGoal));
        }
        return out;
    }

    @Override
    public boolean hasGoal(GoalSet set, GoalKey key) {
        check();
        for (WrappedGoal w : selector(set).getAvailableGoals()) if (GoalKeys.of(w.getGoal()).equals(key)) return true;
        return false;
    }

    @Override
    public void addGoal(FurnacePlugin owner, GoalSet set, int priority, MobGoal goal) {
        check();
        if (owner == null || goal == null || set == null) throw new NullPointerException("owner·set·goal 은 null 일 수 없다");
        if (mob.isRemoved()) return;
        selector(set).addGoal(priority, new PluginGoal(owner, goal, this, set));
    }

    @Override
    public void addGoal(GoalSet set, int priority, VanillaGoal goal) {
        check();
        Goal g = VanillaGoals.create(mob, goal);
        if (mob.isRemoved()) return;
        selector(set).addGoal(priority, g);
    }

    @Override
    public int removeGoals(GoalSet set, GoalKey key) {
        check();
        return selector(set).removeGoalsMatching(g -> GoalKeys.of(g).equals(key));
    }

    @Override
    public boolean removeGoal(GoalSet set, MobGoal goal) {
        check();
        return selector(set).removeGoalsMatching(g -> g instanceof PluginGoal pg && pg.goal == goal) > 0;
    }

    @Override
    public int removeAllGoals(GoalSet set) {
        check();
        return selector(set).removeGoalsMatching(g -> true);
    }

    // ------------------------------------------------------------------ 움직임

    @Override
    public boolean moveTo(double x, double y, double z, double speed) {
        check();
        return mob.getNavigation().moveTo(x, y, z, speed);
    }

    @Override
    public boolean moveTo(UUID entity, double speed) {
        check();
        Targetable t = find(entity);
        return t != null && mob.getNavigation().moveTo(t, speed);
    }

    @Override
    public void stopMoving() {
        check();
        mob.getNavigation().stop();
    }

    @Override
    public boolean isMoving() {
        check();
        return mob.getNavigation().isInProgress();
    }

    @Override
    public void lookAt(double x, double y, double z) {
        check();
        mob.getLookControl().setLookAt(x, y, z);
    }

    @Override
    public void jump() {
        check();
        mob.getJumpControl().jump();
    }

    // ------------------------------------------------------------------ 공격 대상

    @Override
    public Optional<UUID> target() {
        check();
        Targetable t = mob.getTarget();
        if (t instanceof Entity e) return Optional.of(e.uuid());
        if (t instanceof org.domi.furnace.server.entity.Player p) return Optional.of(p.uuid());
        return Optional.empty();
    }

    @Override
    public boolean setTarget(UUID entity) {
        check();
        if (entity == null) return mob.setTarget(null);
        Targetable t = find(entity);
        return t != null && mob.setTarget(t);
    }

    /** 같은 레벨(region)에 살아 있는 엔티티·플레이어. */
    private Targetable find(UUID id) {
        if (id == null) return null;
        Entity e = mob.level().entities().get(id);
        if (e instanceof Targetable t && !e.isRemoved()) return t;
        return mob.level().playerByUuid(id);
    }

    // ------------------------------------------------------------------ 길찾기

    private static final PathNodeType[] API_TYPES;
    private static final Map<String, PathType> BY_KEY = new HashMap<>();

    static {
        PathType[] all = PathType.values();
        API_TYPES = new PathNodeType[all.length];
        for (PathType t : all) {
            API_TYPES[t.ordinal()] = PathNodeType.of(t.name());
            BY_KEY.put(API_TYPES[t.ordinal()].key(), t);
        }
    }

    static PathType toServer(PathNodeType t) {
        PathType s = BY_KEY.get(t.key());
        if (s == null) throw new IllegalArgumentException("이 판에 없는 길 종류: " + t.key());
        return s;
    }

    static PathNodeType toApi(PathType t) { return API_TYPES[t.ordinal()]; }

    @Override
    public float pathCost(PathNodeType type) {
        check();
        return mob.getPathfindingMalus(toServer(type));
    }

    @Override
    public void setPathCost(PathNodeType type, float cost) {
        check();
        if (Float.isNaN(cost)) throw new IllegalArgumentException("비용이 NaN");
        mob.setPathfindingMalus(toServer(type), cost);
    }

    @Override
    public void setPathTypeHook(FurnacePlugin owner, PathTypeHook hook) {
        check();
        if (hook == null) {
            mob.setPathTypeHook(null);
            return;
        }
        if (owner == null) throw new NullPointerException("owner");
        mob.setPathTypeHook(new HookAdapter(mob, owner, hook));
    }

    /** API 훅 → 서버 훅. 플러그인이 꺼졌거나 예외·모르는 종류면 바닐라 값을 쓰고 훅을 뗀다(로그 한 번). */
    private record HookAdapter(Mob mob, FurnacePlugin owner, PathTypeHook hook) implements org.domi.furnace.server.entity.ai.navigation.PathTypeHook {
        @Override
        public PathType apply(int x, int y, int z, PathType vanilla) {
            if (!owner.isEnabled()) {
                detach();
                return vanilla;
            }
            PathNodeType r;
            try {
                r = hook.classify(x, y, z, toApi(vanilla));
            } catch (Throwable t) {
                LoggerFactory.getLogger(MobAiImpl.class).warn("플러그인 {} 의 길찾기 훅이 예외를 던져 뗀다", owner, t);
                detach();
                return vanilla;
            }
            if (r == null) return vanilla;
            PathType s = BY_KEY.get(r.key());
            if (s == null) {
                LoggerFactory.getLogger(MobAiImpl.class).warn("플러그인 {} 의 길찾기 훅이 모르는 종류 {} 를 돌려줘 뗀다", owner, r.key());
                detach();
                return vanilla;
            }
            return s;
        }

        private void detach() {
            if (mob.pathTypeHook() == this) mob.setPathTypeHook(null);
        }
    }
}
