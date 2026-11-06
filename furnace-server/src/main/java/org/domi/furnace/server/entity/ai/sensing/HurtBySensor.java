// 바닐라 HurtBySensor: 최근(40틱) 입은 피해와 그 원인 생물을 기억한다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class HurtBySensor extends Sensor<Mob> {
    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.HURT_BY, MemoryModuleType.HURT_BY_ENTITY); }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) {
        var brain = mob.getBrain();
        String type = mob.getLastDamageType();
        if (type != null) {
            brain.setMemory(MemoryModuleType.HURT_BY, new Entity.Damage(type, false, false, false));
            Targetable attacker = mob.getLastDamageAttacker();
            if (attacker != null) brain.setMemory(MemoryModuleType.HURT_BY_ENTITY, attacker);
        } else {
            brain.eraseMemory(MemoryModuleType.HURT_BY);
        }
        brain.getMemory(MemoryModuleType.HURT_BY_ENTITY).ifPresent(t -> {
            if (!t.isAlive() || (t instanceof Entity e && e.level() != level)) brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        });
    }
}
