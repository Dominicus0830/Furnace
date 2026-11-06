// 바닐라 NearestVisibleLivingEntitySensor: 보이는 생물 중 조건(isMatchingEntity)에 맞는 첫 번째를 기억 하나에 둔다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public abstract class NearestVisibleLivingEntitySensor extends Sensor<Mob> {
    protected abstract boolean isMatchingEntity(Mob owner, Targetable target);

    protected abstract MemoryModuleType<Targetable> getMemory();

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(getMemory()); }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) { mob.getBrain().setMemory(getMemory(), getMatchingEntity(mob)); }

    private Optional<Targetable> getMatchingEntity(Mob mob) {
        return mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).flatMap(v -> v.findClosest(t -> isMatchingEntity(mob, t)));
    }
}
