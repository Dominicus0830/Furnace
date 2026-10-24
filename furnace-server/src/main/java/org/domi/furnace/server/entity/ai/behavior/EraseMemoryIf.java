// 바닐라 EraseMemoryIf: 기억이 있고 조건이 참이면 그 기억을 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class EraseMemoryIf {
    private EraseMemoryIf() {}

    public static <E extends Mob> OneShot<E> create(Predicate<E> predicate, MemoryModuleType<?> memory) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, memory) || !predicate.test(mob)) return false;
                mob.getBrain().eraseMemory(memory);
                return true;
            }
        };
    }
}
