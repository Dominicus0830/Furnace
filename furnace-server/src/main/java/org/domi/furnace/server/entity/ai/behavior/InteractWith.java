// 바닐라 InteractWith: 보이는 같은 종류 몹 하나를 골라 서로 바라보며 다가간다(마을 주민·피글린의 사교).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class InteractWith {
    private InteractWith() {}

    public static <T extends Targetable> OneShot<Mob> of(EntityType type, int interactionRange, MemoryModuleType<T> interactionTarget, float speed, int stopDistance) {
        return of(type, interactionRange, m -> true, t -> true, interactionTarget, speed, stopDistance);
    }

    @SuppressWarnings("unchecked")
    public static <E extends Mob, T extends Targetable> OneShot<E> of(EntityType type, int interactionRange, Predicate<E> selfFilter, Predicate<T> targetFilter,
            MemoryModuleType<T> interactionTarget, float speed, int stopDistance) {
        int rangeSqr = interactionRange * interactionRange;
        Predicate<Targetable> filter = t -> t instanceof Entity e && e.type() == type && targetFilter.test((T) t);
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!registered(mob, interactionTarget) || !registered(mob, MemoryModuleType.LOOK_TARGET) || !absent(mob, MemoryModuleType.WALK_TARGET)
                        || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) return false;
                var visible = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get();
                if (!selfFilter.test(mob) || !visible.contains(filter)) return false;
                var found = visible.findClosest(t -> BehaviorUtils.distSqr(mob, t) <= rangeSqr && filter.test(t));
                found.ifPresent(t -> {
                    mob.getBrain().setMemory(interactionTarget, (T) t);
                    mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(t, true));
                    mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(t, false), speed, stopDistance));
                });
                return true;
            }
        };
    }
}
