// 바닐라 StayCloseToTarget: 따라갈 대상(tooFar 칸보다 멀리 있을 때)에게 closeEnough 까지 걸어간다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.PositionTracker;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class StayCloseToTarget {
    private StayCloseToTarget() {}

    public static <E extends Mob> OneShot<E> create(Function<E, Optional<PositionTracker>> target, Predicate<E> canRun, int closeEnough, int tooFar, float speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.LOOK_TARGET) || !registered(mob, MemoryModuleType.WALK_TARGET)) return false;
                Optional<PositionTracker> t = target.apply(mob);
                if (t.isEmpty() || !canRun.test(mob)) return false;
                double[] p = t.get().currentPosition();
                double dx = mob.x() - p[0], dy = mob.y() - p[1], dz = mob.z() - p[2];
                if (dx * dx + dy * dy + dz * dz < (double) tooFar * tooFar) return false;
                mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, t.get());
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(t.get(), speed, closeEnough));
                return true;
            }
        };
    }
}
