// 바닐라 SocializeAtBell: 모임 장소(종) 4칸 안에서 레벨 난수 1/100 로 보이는 가까운(√32 안) 주민에게 다가가 어울린다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class SocializeAtBell {
    private static final float SPEED = 0.3F;

    private SocializeAtBell() {}

    public static OneShot<Mob> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long time) {
                if (!registered(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.LOOK_TARGET) || !present(mob, MemoryModuleType.MEETING_POINT)
                        || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES) || !absent(mob, MemoryModuleType.INTERACTION_TARGET)) return false;
                GlobalPos g = mob.getBrain().getMemory(MemoryModuleType.MEETING_POINT).get();
                var visible = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get();
                if (level.random().nextInt(100) == 0 && level.dimension().equals(g.dimension()) && ValidateNearbyPoi.closerToCenterThan(g.pos(), mob, 4.0)
                        && visible.contains(t -> t instanceof Entity e && e.type() == EntityType.VILLAGER)) {
                    visible.findClosest(t -> t instanceof Entity e && e.type() == EntityType.VILLAGER && BehaviorUtils.distSqr(mob, t) <= 32.0).ifPresent(t -> {
                        mob.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, t);
                        mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(t, true));
                        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(t, false), SPEED, 1));
                    });
                    return true;
                }
                return false;
            }
        };
    }
}
