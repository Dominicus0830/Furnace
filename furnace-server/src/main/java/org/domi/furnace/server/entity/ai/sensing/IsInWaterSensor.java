// 바닐라 IsInWaterSensor: 물속이면 is_in_water 기억을 둔다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.entity.ai.brain.Unit;

public class IsInWaterSensor extends Sensor<Mob> {
    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.IS_IN_WATER); }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) {
        if (mob.isInWater()) mob.getBrain().setMemory(MemoryModuleType.IS_IN_WATER, Unit.INSTANCE);
        else mob.getBrain().eraseMemory(MemoryModuleType.IS_IN_WATER);
    }
}
