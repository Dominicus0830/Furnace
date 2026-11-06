// 바닐라 SetLookAndInteract: 가까이(distance 칸 안) 보이는 같은 종류 대상을 바라보며 상호작용 대상으로 삼는다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class SetLookAndInteract {
    private SetLookAndInteract() {}

    public static OneShot<Mob> create(java.util.function.Predicate<org.domi.furnace.server.entity.Targetable> filter, int interactionRange) {
        int rangeSqr = interactionRange * interactionRange;
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.LOOK_TARGET) || !absent(mob, MemoryModuleType.INTERACTION_TARGET) || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) return false;
                var found = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().findClosest(t -> BehaviorUtils.distSqr(mob, t) <= rangeSqr && filter.test(t));
                if (found.isEmpty()) return false;
                mob.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, found.get());
                mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(found.get(), true));
                return true;
            }
        };
    }
}
