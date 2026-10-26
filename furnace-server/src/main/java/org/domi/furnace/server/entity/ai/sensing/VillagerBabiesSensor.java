// 바닐라 VillagerBabiesSensor: 보이는 생물 중 아기 주민을 VISIBLE_VILLAGER_BABIES 로.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class VillagerBabiesSensor extends Sensor<Mob> {
    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES); }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) {
        var visible = mob.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
        mob.getBrain().setMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES,
                List.copyOf(visible.stream(t -> t instanceof Entity e && e.type() == EntityType.VILLAGER && t instanceof Mob m && m.isBaby())));
    }
}
