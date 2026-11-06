// 바닐라 SetWalkTargetFromLookTarget: WALK_TARGET 이 없고 LOOK_TARGET 이 있으면 그쪽으로 걷는 목표를 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Function;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class SetWalkTargetFromLookTarget {
    private SetWalkTargetFromLookTarget() {}

    public static OneShot<Mob> create(float speed, int closeEnough) { return create(m -> true, m -> speed, closeEnough); }

    public static OneShot<Mob> create(Predicate<Mob> canSet, Function<Mob, Float> speed, int closeEnough) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET) || !present(mob, MemoryModuleType.LOOK_TARGET)) return false;
                if (!canSet.test(mob)) return false;
                mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(mob.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).get(), speed.apply(mob), closeEnough));
                return true;
            }
        };
    }
}
