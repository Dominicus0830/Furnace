// 바닐라 SetEntityLookTargetSometimes: 가까운(maxDist) 보이는 대상 중 하나를 가끔(무작위 간격) 바라보게 LOOK_TARGET 에 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;

public final class SetEntityLookTargetSometimes {
    private SetEntityLookTargetSometimes() {}

    public static OneShot<Mob> create(float maxDist, UniformInt interval) { return create(maxDist, interval, t -> true); }

    /** 플레이어만 바라본다. */
    public static OneShot<Mob> createPlayers(float maxDist, UniformInt interval) { return create(maxDist, interval, Targetable::isPlayer); }

    public static OneShot<Mob> create(float maxDist, UniformInt interval, Predicate<Targetable> filter) {
        float maxDistSqr = maxDist * maxDist;
        Ticker ticker = new Ticker(interval);
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.LOOK_TARGET) || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) return false;
                NearestVisibleLivingEntities visible = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get();
                Optional<Targetable> target = visible.findClosest(filter.and(t -> {
                    double dx = t.x() - mob.x(), dy = t.y() - mob.y(), dz = t.z() - mob.z();
                    return dx * dx + dy * dy + dz * dz <= maxDistSqr;
                }));
                if (target.isEmpty()) return false;
                if (!ticker.tickDown(level.random())) return false;
                mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target.get(), true));
                return true;
            }
        };
    }

    public static final class Ticker {
        private final UniformInt interval;
        private int ticksUntilNextStart;

        public Ticker(UniformInt interval) {
            if (interval.min() <= 1) throw new IllegalArgumentException();
            this.interval = interval;
        }

        public boolean tickDown(RandomSource r) {
            if (ticksUntilNextStart == 0) {
                ticksUntilNextStart = interval.sample(r) - 1;
                return false;
            }
            return --ticksUntilNextStart == 0;
        }
    }
}
