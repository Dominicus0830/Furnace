// 바닐라 BabyFollowAdult: 아기가 보이는 어른과 너무 멀어지면(최대 거리 초과 전에) 어른 쪽으로 걷는다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Function;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.AgeableMob;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class BabyFollowAdult {
    private BabyFollowAdult() {}

    public static OneShot<AgeableMob> create(UniformInt followRange, float speed) { return create(followRange, m -> speed); }

    public static OneShot<AgeableMob> create(UniformInt followRange, Function<Mob, Float> speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, AgeableMob mob, long gameTime) {
                if (!present(mob, MemoryModuleType.NEAREST_VISIBLE_ADULT) || !registered(mob, MemoryModuleType.LOOK_TARGET) || !absent(mob, MemoryModuleType.WALK_TARGET)) return false;
                if (!mob.isBaby()) return false;
                AgeableMob adult = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT).get();
                double max = followRange.max() + 1, min = followRange.min();
                double d = BehaviorUtils.distSqr(mob, adult);
                if (d < max * max && !(d < min * min)) {
                    mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(adult, true));
                    mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(adult, false), speed.apply(mob), followRange.min() - 1));
                    return true;
                }
                return false;
            }
        };
    }
}
