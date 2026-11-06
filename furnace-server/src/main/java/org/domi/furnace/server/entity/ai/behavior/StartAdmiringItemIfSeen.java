// 바닐라 StartAdmiringItemIfSeen: 보이는 원하는 아이템이 좋아하는 아이템(금)이면 감상 상태에 들어간다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.PiglinAi;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StartAdmiringItemIfSeen {
    private StartAdmiringItemIfSeen() {}

    public static <E extends Mob> OneShot<E> create(int admireDuration) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM) || !absent(mob, MemoryModuleType.ADMIRING_ITEM) || !absent(mob, MemoryModuleType.ADMIRING_DISABLED)
                        || !absent(mob, MemoryModuleType.DISABLE_WALK_TO_ADMIRE_ITEM)) return false;
                var item = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).get();
                if (!PiglinAi.isLovedItem(item.getItem())) return false;
                mob.getBrain().setMemoryWithExpiry(MemoryModuleType.ADMIRING_ITEM, true, admireDuration);
                return true;
            }
        };
    }
}
