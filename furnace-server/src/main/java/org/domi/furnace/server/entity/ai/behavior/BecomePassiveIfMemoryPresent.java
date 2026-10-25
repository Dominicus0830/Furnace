// 바닐라 BecomePassiveIfMemoryPresent: 기억이 있으면 공격 대상을 지우고 일정 시간 얌전해진다(PACIFIED).
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class BecomePassiveIfMemoryPresent {
    private BecomePassiveIfMemoryPresent() {}

    public static <E extends Mob> OneShot<E> create(MemoryModuleType<?> pacifyingMemory, int pacifiedDuration) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.ATTACK_TARGET) || !absent(mob, MemoryModuleType.PACIFIED) || !present(mob, pacifyingMemory)) return false;
                mob.getBrain().setMemoryWithExpiry(MemoryModuleType.PACIFIED, true, pacifiedDuration);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return true;
            }
        };
    }
}
