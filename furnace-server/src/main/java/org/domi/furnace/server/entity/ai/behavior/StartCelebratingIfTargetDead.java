// 바닐라 StartCelebratingIfTargetDead: 공격 대상이 죽으면 조건에 따라 그 자리에서 축하(춤)하고, 분노와 공격 목표를 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.function.BiPredicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StartCelebratingIfTargetDead {
    private StartCelebratingIfTargetDead() {}

    public static <E extends Mob> OneShot<E> create(int celebrateDuration, BiPredicate<E, Targetable> dancePredicate) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, MemoryModuleType.ATTACK_TARGET) || !registered(mob, MemoryModuleType.ANGRY_AT) || !absent(mob, MemoryModuleType.CELEBRATE_LOCATION)
                        || !registered(mob, MemoryModuleType.DANCING)) return false;
                var brain = mob.getBrain();
                Targetable target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
                boolean dead = target instanceof LivingEntity le ? le.isDeadOrDying() : !target.isAlive();
                if (!dead) return false;
                if (dancePredicate.test(mob, target)) brain.setMemoryWithExpiry(MemoryModuleType.DANCING, true, celebrateDuration);
                brain.setMemoryWithExpiry(MemoryModuleType.CELEBRATE_LOCATION, target.blockPosition(), celebrateDuration);
                if (!target.isPlayer() || level.gameRule("forgiveDeadPlayers")) {
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    brain.eraseMemory(MemoryModuleType.ANGRY_AT);
                }
                return true;
            }
        };
    }
}
