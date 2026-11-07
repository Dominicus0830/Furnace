// 바닐라 DismountOrSkipMounting: 타고 있는 것(없으면 탈 대상)이 죽었거나, 거리 밖이거나, 다른 레벨이거나, 그만 탈 조건이면 내리고 RIDE_TARGET 을 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.BiPredicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class DismountOrSkipMounting {
    private DismountOrSkipMounting() {}

    public static <E extends Mob> OneShot<E> create(int maxWalkDistToRideTarget, BiPredicate<E, Entity> dontRideIf) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.RIDE_TARGET)) return false;
                Entity vehicle = mob.vehicle();
                Entity target = mob.getBrain().getMemory(MemoryModuleType.RIDE_TARGET).orElse(null);
                if (vehicle == null && target == null) return false;
                Entity e = vehicle == null ? target : vehicle;
                if (isWithinDistance(mob, e, maxWalkDistToRideTarget) && !dontRideIf.test(mob, e)) return false;
                mob.stopRiding();
                mob.getBrain().eraseMemory(MemoryModuleType.RIDE_TARGET);
                return true;
            }
        };
    }

    private static boolean isWithinDistance(Mob mob, Entity e, int dist) {
        return e.isAlive() && Mount.closerThan(e, mob, dist) && e.level() == mob.level();
    }
}
