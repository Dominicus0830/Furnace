// 바닐라 SetEntityLookTarget: 조건에 맞는 보이는 가장 가까운 생물이 maxDist 안이면 그쪽을 바라보는 LOOK_TARGET 을 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class SetEntityLookTarget {
    private SetEntityLookTarget() {}

    public static OneShot<Mob> create(Predicate<Targetable> predicate, float maxDist) {
        float maxDistSqr = maxDist * maxDist;
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.LOOK_TARGET) || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) return false;
                var found = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().findClosest(predicate.and(t -> BehaviorUtils.distSqr(mob, t) <= maxDistSqr));
                if (found.isEmpty()) return false;
                mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(found.get(), true));
                return true;
            }
        };
    }

    public static OneShot<Mob> createPlayers(float maxDist) { return create(Targetable::isPlayer, maxDist); }
}
