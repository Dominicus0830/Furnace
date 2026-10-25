// 바닐라 RememberIfHoglinWasKilled: 공격 대상 호글린이 죽었으면 한동안 사냥하지 않도록 기억한다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.AbstractPiglin;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.PiglinAi;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class RememberIfHoglinWasKilled {
    private RememberIfHoglinWasKilled() {}

    public static OneShot<AbstractPiglin> create() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, AbstractPiglin mob, long gameTime) {
                if (!present(mob, MemoryModuleType.ATTACK_TARGET) || !registered(mob, MemoryModuleType.HUNTED_RECENTLY)) return false;
                var target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
                if (target instanceof Entity e && e.type() == EntityType.HOGLIN && target instanceof LivingEntity le && le.isDeadOrDying()) {
                    mob.getBrain().setMemoryWithExpiry(MemoryModuleType.HUNTED_RECENTLY, true, PiglinAi.TIME_BETWEEN_HUNTS.sample(level.random()));
                }
                return true;
            }
        };
    }
}
