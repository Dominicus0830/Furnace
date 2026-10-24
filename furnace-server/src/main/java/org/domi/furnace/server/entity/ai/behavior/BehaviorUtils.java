// 바닐라 BehaviorUtils: 브레인 행동들이 공유하는 도우미(서로 바라보며 걷기, 시선/걷기 기억 설정, 거리 비교 등).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PathfinderMob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.BlockPosTracker;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.PositionTracker;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;
import org.domi.furnace.server.entity.ai.util.LandRandomPos;

public final class BehaviorUtils {
    private BehaviorUtils() {}

    public static void lockGazeAndWalkToEachOther(Mob a, Mob b, float speed, int closeEnough) {
        lookAtEachOther(a, b);
        setWalkAndLookTargetMemoriesToEachOther(a, b, speed, closeEnough);
    }

    public static boolean entityIsVisible(Brain<?> brain, Targetable t) {
        Optional<NearestVisibleLivingEntities> v = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
        return v.isPresent() && v.get().contains(t);
    }

    public static boolean targetIsValid(Brain<?> brain, MemoryModuleType<? extends Targetable> type, Predicate<Targetable> filter) {
        return brain.getMemory(type).map(t -> (Targetable) t).filter(filter).filter(Targetable::isAlive).filter(t -> entityIsVisible(brain, t)).isPresent();
    }

    public static boolean targetIsValid(Brain<?> brain, MemoryModuleType<? extends Targetable> type, org.domi.furnace.server.entity.EntityType et) {
        return targetIsValid(brain, type, t -> t instanceof Entity e && e.type() == et);
    }

    private static void lookAtEachOther(Mob a, Mob b) {
        lookAtEntity(a, b);
        lookAtEntity(b, a);
    }

    public static void lookAtEntity(Mob mob, Targetable target) { mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true)); }

    private static void setWalkAndLookTargetMemoriesToEachOther(Mob a, Mob b, float speed, int closeEnough) {
        setWalkAndLookTargetMemories(a, b, speed, closeEnough);
        setWalkAndLookTargetMemories(b, a, speed, closeEnough);
    }

    public static void setWalkAndLookTargetMemories(Mob mob, Targetable target, float speed, int closeEnough) {
        setWalkAndLookTargetMemories(mob, new EntityTracker(target, true), speed, closeEnough);
    }

    public static void setWalkAndLookTargetMemories(Mob mob, Pos pos, float speed, int closeEnough) {
        setWalkAndLookTargetMemories(mob, new BlockPosTracker(pos), speed, closeEnough);
    }

    public static void setWalkAndLookTargetMemories(Mob mob, PositionTracker tracker, float speed, int closeEnough) {
        WalkTarget w = new WalkTarget(tracker, speed, closeEnough);
        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, tracker);
        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, w);
    }

    /** BehaviorUtils.isWithinAttackRange(mob, target, extra): 손에 든 발사 무기의 사거리 또는 근접 거리 안인가. TODO(발사 무기): 무기 사거리 */
    public static boolean isWithinAttackRange(Mob mob, Targetable target, int extra) {
        return mob.isWithinMeleeAttackRange(target);
    }

    public static boolean isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(Mob mob, Targetable other, double extra) {
        Optional<Targetable> cur = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (cur.isEmpty()) return false;
        double a = distSqr(mob, cur.get()), b = distSqr(mob, other);
        return b > a + extra * extra;
    }

    public static boolean canSee(Mob mob, Targetable target) {
        Brain<?> brain = mob.getBrain();
        return brain.hasMemoryValue(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) && brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().contains(target);
    }

    public static Targetable getNearestTarget(Mob mob, Optional<Targetable> a, Targetable b) { return a.isEmpty() ? b : getNearestTarget(mob, a.get(), b); }

    public static Targetable getNearestTarget(Mob mob, Targetable a, Targetable b) { return distSqr(mob, a) < distSqr(mob, b) ? a : b; }

    public static Optional<Targetable> getLivingEntityFromUUIDMemory(Mob mob, MemoryModuleType<UUID> type) {
        Optional<UUID> id = mob.getBrain().getMemory(type);
        return id.map(u -> mob.level().getTargetableByUuid(u)).filter(t -> t != null);
    }

    public static double distSqr(Mob a, Targetable b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** BehaviorUtils.getRandomSwimmablePos 와 같은 방식으로 땅 위 무작위 점을 찾되 길찾기 가능한 곳만(최대 10회). */
    public static double[] getRandomLandPos(PathfinderMob mob, int h, int v) {
        double[] p = LandRandomPos.getPos(mob, h, v);
        int tries = 0;
        while (p != null && !org.domi.furnace.server.block.Pathfinding.isPathfindable(mob.level().getBlockState(new Pos((int) Math.floor(p[0]), (int) Math.floor(p[1]), (int) Math.floor(p[2]))), org.domi.furnace.server.block.Pathfinding.LAND) && tries++ < 10) {
            p = LandRandomPos.getPos(mob, h, v);
        }
        return p;
    }

    public static boolean isBreeding(Mob mob) { return mob.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET); }

    public static List<Targetable> toList(Iterable<Targetable> it) {
        List<Targetable> out = new java.util.ArrayList<>();
        for (Targetable t : it) out.add(t);
        return out;
    }
}
