// 바닐라 SetWalkTargetFromAttackTargetIfTargetOutOfReach: 공격 대상이 사거리 밖이면 그쪽으로 걷는 목표를 두고, 안이면 걷기를 멈춘다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Function;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class SetWalkTargetFromAttackTargetIfTargetOutOfReach {
    private SetWalkTargetFromAttackTargetIfTargetOutOfReach() {}

    public static OneShot<Mob> create(float speed) { return create(m -> speed); }

    public static OneShot<Mob> create(Function<Mob, Float> speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.LOOK_TARGET) || !present(mob, MemoryModuleType.ATTACK_TARGET)) return false;
                var brain = mob.getBrain();
                Targetable target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
                var visible = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
                if (visible.isPresent() && visible.get().contains(target) && BehaviorUtils.isWithinAttackRange(mob, target, 1)) {
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                } else {
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                    brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(target, false), speed.apply(mob), 0));
                }
                return true;
            }
        };
    }
}
