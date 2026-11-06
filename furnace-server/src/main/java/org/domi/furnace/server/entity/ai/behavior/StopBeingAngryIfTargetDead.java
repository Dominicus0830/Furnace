// 바닐라 StopBeingAngryIfTargetDead: 분노 대상이 죽었으면(플레이어는 죽은 플레이어 용서 규칙일 때) 분노를 푼다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StopBeingAngryIfTargetDead {
    private StopBeingAngryIfTargetDead() {}

    public static <E extends Mob> OneShot<E> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, MemoryModuleType.ANGRY_AT)) return false;
                BehaviorUtils.getLivingEntityFromUUIDMemory(mob, MemoryModuleType.ANGRY_AT).filter(t -> t instanceof LivingEntity le ? le.isDeadOrDying() : !t.isAlive())
                        .filter(t -> !t.isPlayer() || level.gameRule("forgiveDeadPlayers")).ifPresent(t -> mob.getBrain().eraseMemory(MemoryModuleType.ANGRY_AT));
                return true;
            }
        };
    }
}
