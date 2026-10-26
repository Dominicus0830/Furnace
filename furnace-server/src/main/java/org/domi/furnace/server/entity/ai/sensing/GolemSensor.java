// 바닐라 GolemSensor(200틱): 주변 생물(NEAREST_LIVING_ENTITIES)에 철 골렘이 있으면 GOLEM_DETECTED_RECENTLY 를 599틱 둔다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class GolemSensor extends Sensor<Mob> {
    public static final int GOLEM_SCAN_RATE = 200, MEMORY_TIME_TO_LIVE = 599;

    public GolemSensor() { this(GOLEM_SCAN_RATE); }

    public GolemSensor(int scanRate) { super(scanRate); }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) { checkForNearbyGolem(mob); }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES); }

    public static void checkForNearbyGolem(Mob mob) {
        var list = mob.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (list.isPresent() && list.get().stream().anyMatch(t -> t instanceof Entity e && e.type() == EntityType.IRON_GOLEM)) golemDetected(mob);
    }

    public static void golemDetected(Mob mob) { mob.getBrain().setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, MEMORY_TIME_TO_LIVE); }
}
