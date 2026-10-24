// 바닐라 StartAttacking: 공격 대상이 없고 후보가 있으면 ATTACK_TARGET 으로 정한다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StartAttacking {
    private StartAttacking() {}

    public static <E extends Mob> OneShot<E> create(Function<E, Optional<? extends Targetable>> finder) { return create(m -> true, finder); }

    public static <E extends Mob> OneShot<E> create(Predicate<E> canAttack, Function<E, Optional<? extends Targetable>> finder) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!absent(mob, MemoryModuleType.ATTACK_TARGET) || !registered(mob, MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) return false;
                if (!canAttack.test(mob)) return false;
                Optional<? extends Targetable> t = finder.apply(mob);
                if (t.isEmpty()) return false;
                Targetable target = t.get();
                if (!mob.canAttackType(target) || !target.canBeSeenAsEnemy()) return false;
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                mob.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                return true;
            }
        };
    }
}
