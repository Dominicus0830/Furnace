// 바닐라 AvoidEntityGoal: 위험한 대상이 가까이 오면 반대쪽으로 달아난다.
package org.domi.furnace.server.entity.ai.goal;

import java.util.EnumSet;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.Goal;
import org.domi.furnace.server.entity.ai.navigation.Path;
import org.domi.furnace.server.entity.ai.navigation.PathNavigation;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;
import org.domi.furnace.server.world.GameMode;

public class AvoidEntityGoal extends Goal {
    protected final PathfinderMob mob;
    private final double walkSpeedModifier, sprintSpeedModifier;
    protected Targetable toAvoid;
    protected final float maxDist;
    protected Path path;
    protected final PathNavigation pathNav;
    protected final Class<? extends Targetable> avoidClass;
    protected final Predicate<Targetable> avoidPredicate;
    private final TargetingConditions avoidEntityTargeting;

    public AvoidEntityGoal(PathfinderMob mob, Class<? extends Targetable> cls, float maxDist, double walk, double sprint) {
        this(mob, cls, t -> true, maxDist, walk, sprint, t -> !(t instanceof Player p) || p.gameMode() != GameMode.CREATIVE && p.gameMode() != GameMode.SPECTATOR);
    }

    /** 피할 대상 조건(predicateOnAvoidEntity)이 기본 '크리에이티브·관전자 제외'를 대신한다. */
    public AvoidEntityGoal(PathfinderMob mob, Class<? extends Targetable> cls, float maxDist, double walk, double sprint, Predicate<Targetable> predicateOnAvoidEntity) {
        this(mob, cls, t -> true, maxDist, walk, sprint, predicateOnAvoidEntity);
    }

    public AvoidEntityGoal(PathfinderMob mob, Class<? extends Targetable> cls, Predicate<Targetable> avoidPredicate, float maxDist, double walk, double sprint,
            Predicate<Targetable> pathableTargetPredicate) {
        this.mob = mob;
        this.avoidClass = cls;
        this.avoidPredicate = avoidPredicate;
        this.maxDist = maxDist;
        this.walkSpeedModifier = walk;
        this.sprintSpeedModifier = sprint;
        this.pathNav = mob.getNavigation();
        setFlags(EnumSet.of(Flag.MOVE));
        this.avoidEntityTargeting = TargetingConditions.forCombat().range(maxDist).selector(pathableTargetPredicate.and(avoidPredicate));
    }

    @Override
    public boolean canUse() {
        toAvoid = nearest();
        if (toAvoid == null) return false;
        double[] pos = LandRandomPos.getPosAway(mob, 16, 7, new double[] {toAvoid.x(), toAvoid.y(), toAvoid.z()});
        if (pos == null) return false;
        if (dist(toAvoid, pos[0], pos[1], pos[2]) < dist(toAvoid, mob.x(), mob.y(), mob.z())) return false;
        path = pathNav.createPath(pos[0], pos[1], pos[2], 0);
        return path != null;
    }

    private static double dist(Targetable t, double x, double y, double z) {
        double dx = t.x() - x, dy = t.y() - y, dz = t.z() - z;
        return dx * dx + dy * dy + dz * dz;
    }

    private Targetable nearest() {
        var level = mob.level();
        var box = mob.boundingBox().inflate(maxDist, 3.0, maxDist);
        java.util.List<Targetable> cand = new java.util.ArrayList<>();
        if (avoidClass.isAssignableFrom(Player.class)) {
            level.forEachPlayerIn(box, cand::add);
        }
        if (Entity.class.isAssignableFrom(avoidClass)) {
            @SuppressWarnings("unchecked")
            Class<? extends Entity> ec = (Class<? extends Entity>) avoidClass;
            for (Entity e : level.getEntitiesOfClass(ec, box, x -> true)) if (e instanceof Targetable t) cand.add(t);
        }
        double best = -1.0;
        Targetable found = null;
        for (Targetable t : cand) {
            if (!avoidEntityTargeting.test(mob, t)) continue;
            double d = dist(t, mob.x(), mob.y(), mob.z());
            if (best == -1.0 || d < best) {
                best = d;
                found = t;
            }
        }
        return found;
    }

    @Override
    public boolean canContinueToUse() { return !pathNav.isDone(); }

    @Override
    public void start() { pathNav.moveTo(path, walkSpeedModifier); }

    @Override
    public void stop() { toAvoid = null; }

    @Override
    public void tick() {
        if (dist(toAvoid, mob.x(), mob.y(), mob.z()) < 49.0) pathNav.setSpeedModifier(sprintSpeedModifier);
        else pathNav.setSpeedModifier(walkSpeedModifier);
    }
}
