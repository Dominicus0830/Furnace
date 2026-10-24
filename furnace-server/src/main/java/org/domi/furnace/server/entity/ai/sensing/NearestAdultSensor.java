// 바닐라 AdultSensor: 보이는 같은 종류 어른 하나를 기억한다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.AgeableMob;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class NearestAdultSensor extends Sensor<AgeableMob> {
    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_VISIBLE_ADULT, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES); }

    @Override
    protected void doTick(ChunkLevel level, AgeableMob mob) {
        mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).ifPresent(v -> setNearestVisibleAdult(mob, v));
    }

    private void setNearestVisibleAdult(AgeableMob mob, NearestVisibleLivingEntities visible) {
        var adult = visible.findClosest(t -> t instanceof Entity e && e.type() == mob.type() && !((AgeableMob) t).isBaby()).map(AgeableMob.class::cast);
        mob.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT, adult);
    }
}
