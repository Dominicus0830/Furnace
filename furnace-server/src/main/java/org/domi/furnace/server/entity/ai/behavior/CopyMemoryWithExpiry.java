// 바닐라 CopyMemoryWithExpiry: 조건이 맞고 출발 기억이 있으면 그 값을 도착 기억에 일정 시간 동안 복사한다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class CopyMemoryWithExpiry {
    private CopyMemoryWithExpiry() {}

    public static <E extends Mob, T> OneShot<E> create(Predicate<E> predicate, MemoryModuleType<? extends T> source, MemoryModuleType<T> target, UniformInt duration) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, source) || !absent(mob, target)) return false;
                if (!predicate.test(mob)) return false;
                mob.getBrain().setMemoryWithExpiry(target, mob.getBrain().getMemory(source).get(), duration.sample(level.random()));
                return true;
            }
        };
    }
}
