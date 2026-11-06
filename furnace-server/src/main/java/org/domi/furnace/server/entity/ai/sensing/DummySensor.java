// 바닐라 DummySensor: 아무것도 하지 않는 센서.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class DummySensor extends Sensor<Mob> {
    @Override protected void doTick(ChunkLevel level, Mob entity) { }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(); }
}
