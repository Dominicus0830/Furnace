// 바닐라 GoToWantedItem: 보이는 원하는 아이템 쪽을 바라보고 걸어간다(주운 직후 쿨다운 중에는 가지 않는다).
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.ItemEntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class GoToWantedItem {
    private GoToWantedItem() {}

    public static <E extends Mob> OneShot<E> create(float speed, boolean interruptWalk, int maxDistToWalk) { return create(m -> true, speed, interruptWalk, maxDistToWalk); }

    public static <E extends Mob> OneShot<E> create(Predicate<E> canWalk, float speed, boolean interruptWalk, int maxDistToWalk) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.LOOK_TARGET) || !(interruptWalk ? registered(mob, MemoryModuleType.WALK_TARGET) : absent(mob, MemoryModuleType.WALK_TARGET))
                        || !present(mob, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM)) return false;
                ItemEntity item = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).get();
                if (mob.getBrain().hasMemoryValue(MemoryModuleType.ITEM_PICKUP_COOLDOWN_TICKS) || !canWalk.test(mob)) return false;
                double dx = item.x() - mob.x(), dy = item.y() - mob.y(), dz = item.z() - mob.z();
                if (!(dx * dx + dy * dy + dz * dz < (double) maxDistToWalk * maxDistToWalk) || !level.withinBorder(item.blockPosition())) return false;
                mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new ItemEntityTracker(item, true));
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new ItemEntityTracker(item, false), speed, 0));
                return true;
            }
        };
    }
}
