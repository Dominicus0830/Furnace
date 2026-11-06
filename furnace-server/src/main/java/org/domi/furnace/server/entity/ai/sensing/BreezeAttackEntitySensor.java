// 바닐라 BreezeAttackEntitySensor: 브리즈의 공격 후보 — 24칸 안에서 가장 가까운, 공격할 수 있는 생물 하나.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Breeze;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class BreezeAttackEntitySensor extends NearestLivingEntitySensor<Breeze> {
    @Override
    protected void doTick(ChunkLevel level, Breeze breeze) {
        super.doTick(level, breeze);
        List<Targetable> list = breeze.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of());
        Targetable found = null;
        for (Targetable t : list) {
            if (t.isAlive() && Sensor.isEntityAttackable(breeze, t)) {
                found = t;
                break;
            }
        }
        if (found != null) breeze.getBrain().setMemory(MemoryModuleType.NEAREST_ATTACKABLE, found);
        else breeze.getBrain().eraseMemory(MemoryModuleType.NEAREST_ATTACKABLE);
    }

    @Override protected int radiusXZ() { return 24; }

    @Override protected int radiusY() { return 24; }

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_ATTACKABLE);
    }
}
