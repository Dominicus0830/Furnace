// 바닐라 ValidatePlayDead: 죽은 척 남은 틱을 줄이고, 끝나면 기억을 지우고 기본 활동으로 돌아간다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class ValidatePlayDead {
    private ValidatePlayDead() {}

    public static OneShot<Mob> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!present(mob, MemoryModuleType.PLAY_DEAD_TICKS) || !registered(mob, MemoryModuleType.HURT_BY_ENTITY)) return false;
                var brain = mob.getBrain();
                int ticks = brain.getMemory(MemoryModuleType.PLAY_DEAD_TICKS).get();
                if (ticks <= 0) {
                    brain.eraseMemory(MemoryModuleType.PLAY_DEAD_TICKS);
                    brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
                    brain.useDefaultActivity();
                } else {
                    brain.setMemory(MemoryModuleType.PLAY_DEAD_TICKS, ticks - 1);
                }
                return true;
            }
        };
    }
}
