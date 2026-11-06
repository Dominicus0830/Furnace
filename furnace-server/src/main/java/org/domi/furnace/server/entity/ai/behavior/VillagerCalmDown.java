// 바닐라 VillagerCalmDown: 맞은 기억·가까운 적이 없고 때린 쪽이 6칸 밖이면 기억을 지우고 시간표대로 활동을 되돌린다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class VillagerCalmDown {
    private static final int SAFE_DISTANCE_FROM_DANGER = 36;

    private VillagerCalmDown() {}

    public static OneShot<Mob> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!registered(mob, MemoryModuleType.HURT_BY) || !registered(mob, MemoryModuleType.HURT_BY_ENTITY) || !registered(mob, MemoryModuleType.NEAREST_HOSTILE)) return false;
                var brain = mob.getBrain();
                boolean danger = brain.getMemory(MemoryModuleType.HURT_BY).isPresent() || brain.getMemory(MemoryModuleType.NEAREST_HOSTILE).isPresent()
                        || brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).filter(t -> BehaviorUtils.distSqr(mob, t) <= SAFE_DISTANCE_FROM_DANGER).isPresent();
                if (!danger) {
                    brain.eraseMemory(MemoryModuleType.HURT_BY);
                    brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                    brain.updateActivityFromSchedule(level.dayTime(), level.gameTime());
                }
                return true;
            }
        };
    }
}
