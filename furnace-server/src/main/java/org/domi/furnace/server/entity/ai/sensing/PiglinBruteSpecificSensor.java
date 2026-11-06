// 바닐라 PiglinBruteSpecificSensor: 브루트가 주목할 것 — 보이는 숙적(위더·위더 스켈레톤)과 근처 어른 피글린들.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.AbstractPiglin;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class PiglinBruteSpecificSensor extends Sensor<Mob> {
    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_NEMESIS, MemoryModuleType.NEARBY_ADULT_PIGLINS); }

    @Override
    protected void doTick(ChunkLevel level, Mob owner) {
        var brain = owner.getBrain();
        List<Mob> nearby = new ArrayList<>();
        NearestVisibleLivingEntities visible = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
        Optional<Targetable> nemesis = visible.findClosest(t -> t instanceof Entity e && (e.type() == EntityType.WITHER_SKELETON || e.type() == EntityType.WITHER));
        for (Targetable t : brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).orElse(List.of())) {
            if (t instanceof AbstractPiglin ap && ap.isAdult()) nearby.add(ap);
        }
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS, nemesis.map(t -> (Mob) t));
        brain.setMemory(MemoryModuleType.NEARBY_ADULT_PIGLINS, nearby);
    }
}
