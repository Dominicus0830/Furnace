// 바닐라 StopAttackingIfTargetInvalid: 대상이 죽었거나 공격할 수 없거나 너무 오래 못 닿으면 ATTACK_TARGET 을 지운다.
package org.domi.furnace.server.entity.ai.behavior;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class StopAttackingIfTargetInvalid {
    private static final int TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE = 200;

    private StopAttackingIfTargetInvalid() {}

    public static <E extends Mob> OneShot<E> create() { return create(t -> false, (m, t) -> { }, true); }

    public static <E extends Mob> OneShot<E> create(Predicate<Targetable> stopWhen) { return create(stopWhen, (m, t) -> { }, true); }

    public static <E extends Mob> OneShot<E> create(Predicate<Targetable> stopWhen, BiConsumer<E, Targetable> onErased, boolean allowTired) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, E mob, long gameTime) {
                if (!present(mob, MemoryModuleType.ATTACK_TARGET)) return false;
                Targetable target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
                Optional<Long> since = mob.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
                boolean sameLevel = !(target instanceof Entity e) || e.level() == mob.level();
                if (mob.canAttackType(target) && target.canBeSeenAsEnemy() && (!allowTired || !isTired(mob, since)) && target.isAlive() && sameLevel && !stopWhen.test(target)) return true;
                onErased.accept(mob, target);
                mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                return true;
            }
        };
    }

    private static boolean isTired(Mob mob, Optional<Long> since) { return since.isPresent() && mob.level().gameTime() - since.get() > TIMEOUT_TO_GET_WITHIN_ATTACK_RANGE; }
}
