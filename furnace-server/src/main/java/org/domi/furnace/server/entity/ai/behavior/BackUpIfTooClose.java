// 바닐라 BackUpIfTooClose: 공격 대상이 tooCloseDistance 안이면 대상을 보며 뒤로 물러난다(석궁 든 피글린).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.control.Mth;

public final class BackUpIfTooClose {
    private BackUpIfTooClose() {}

    public static OneShot<Mob> create(int tooCloseDistance, float strafeSpeed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.LOOK_TARGET) || !present(mob, MemoryModuleType.ATTACK_TARGET)
                        || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) return false;
                var brain = mob.getBrain();
                Targetable target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
                if (BehaviorUtils.distSqr(mob, target) < (double) tooCloseDistance * tooCloseDistance && brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().contains(target)) {
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                    mob.getMoveControl().strafe(-strafeSpeed, 0.0F);
                    mob.setYRot(Mth.rotateIfNecessary(mob.yRot(), mob.yHeadRot(), 0.0F));
                    return true;
                }
                return false;
            }
        };
    }
}
