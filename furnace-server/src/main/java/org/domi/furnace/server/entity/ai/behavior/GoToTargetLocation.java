// 바닐라 GoToTargetLocation: 기억한 위치에서 closeEnough 보다 멀면 그 근처 무작위 칸을 걷는 목표로 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class GoToTargetLocation {
    private GoToTargetLocation() {}

    public static <E extends Mob> OneShot<E> create(MemoryModuleType<Pos> locationMemory, int closeEnoughDist, float speed) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, locationMemory) || !absent(mob, MemoryModuleType.ATTACK_TARGET) || !absent(mob, MemoryModuleType.WALK_TARGET) || !registered(mob, MemoryModuleType.LOOK_TARGET)) return false;
                Pos pos = mob.getBrain().getMemory(locationMemory).get();
                Pos here = mob.blockPosition();
                int dx = pos.x() - here.x(), dy = pos.y() - here.y(), dz = pos.z() - here.z();
                if (!((double) (dx * dx + dy * dy + dz * dz) < (double) closeEnoughDist * closeEnoughDist)) {
                    var r = level.random();
                    Pos target = new Pos(pos.x() + r.nextInt(3) - 1, pos.y(), pos.z() + r.nextInt(3) - 1);
                    BehaviorUtils.setWalkAndLookTargetMemories(mob, target, speed, closeEnoughDist);
                }
                return true;
            }
        };
    }
}
