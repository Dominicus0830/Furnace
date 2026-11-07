// 바닐라 Mount: 탈 대상(RIDE_TARGET)이 1칸 안이면 올라타고, 아니면 그쪽을 보며 걸어간다. 이미 타고 있으면 아무것도 하지 않는다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class Mount {
    private static final int CLOSE_ENOUGH_TO_START_RIDING_DIST = 1;

    private Mount() {}

    public static OneShot<Mob> create(float speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.LOOK_TARGET) || !absent(mob, MemoryModuleType.WALK_TARGET) || !present(mob, MemoryModuleType.RIDE_TARGET)) return false;
                if (mob.isPassenger()) return false;
                Entity e = mob.getBrain().getMemory(MemoryModuleType.RIDE_TARGET).get();
                if (closerThan(e, mob, CLOSE_ENOUGH_TO_START_RIDING_DIST)) {
                    mob.startRiding(e);
                } else if (e instanceof Targetable t) {
                    mob.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(t, true));
                    mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(t, false), speed, 1));
                }
                return true;
            }
        };
    }

    /** Entity.closerThan(other, d): 위치 사이 거리² < d². */
    static boolean closerThan(Entity a, Entity b, double d) {
        double dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz < d * d;
    }
}
