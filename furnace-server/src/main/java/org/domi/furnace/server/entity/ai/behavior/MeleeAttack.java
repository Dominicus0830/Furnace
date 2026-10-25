// 바닐라 MeleeAttack: 공격 대상이 근접 거리에 보이면 바라보고 때린 뒤 cooldown 틱 동안 ATTACK_COOLING_DOWN 을 둔다.
package org.domi.furnace.server.entity.ai.behavior;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.EntityTracker;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public final class MeleeAttack {
    private MeleeAttack() {}

    public static OneShot<Mob> create(int cooldownBetweenAttacks) {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Mob mob, long gameTime) {
                if (!registered(mob, MemoryModuleType.LOOK_TARGET) || !present(mob, MemoryModuleType.ATTACK_TARGET) || !absent(mob, MemoryModuleType.ATTACK_COOLING_DOWN)
                        || !present(mob, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES)) return false;
                if (mob.isHoldingUsableProjectileWeapon()) return false;
                var brain = mob.getBrain();
                Targetable target = brain.getMemory(MemoryModuleType.ATTACK_TARGET).get();
                if (mob.isWithinMeleeAttackRange(target) && brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).get().contains(target)) {
                    brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
                    mob.swing(0);
                    mob.doHurtTarget(target);
                    brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_COOLING_DOWN, true, cooldownBetweenAttacks);
                    return true;
                }
                return false;
            }
        };
    }
}
