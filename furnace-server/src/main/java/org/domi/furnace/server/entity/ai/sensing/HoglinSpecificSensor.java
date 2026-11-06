// 바닐라 HoglinSpecificSensor: 호글린이 피하는 물건(8칸·4칸 안 호글린 기피 블록)과 보이는 어른 피글린·호글린 수를 기억한다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Hoglin;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Piglin;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;
import org.domi.furnace.server.world.gen.feature.BlockPosIter;

public class HoglinSpecificSensor extends Sensor<Hoglin> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return Set.of(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, MemoryModuleType.NEAREST_REPELLENT, MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN,
                MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS, MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT);
    }

    @Override
    protected void doTick(ChunkLevel level, Hoglin hoglin) {
        var brain = hoglin.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_REPELLENT, findNearestRepellent(level, hoglin));
        Optional<Mob> piglin = Optional.empty();
        int piglins = 0;
        List<Mob> hoglins = new ArrayList<>();
        NearestVisibleLivingEntities visible = brain.getMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElse(NearestVisibleLivingEntities.empty());
        for (Targetable t : visible.findAll(x -> x instanceof Mob m && !m.isBaby() && (x instanceof Piglin || x instanceof Hoglin))) {
            if (t instanceof Piglin p) {
                piglins++;
                if (piglin.isEmpty()) piglin = Optional.of(p);
            }
            if (t instanceof Hoglin h) hoglins.add(h);
        }
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN, piglin);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS, hoglins);
        brain.setMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT, piglins);
        brain.setMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT, hoglins.size());
    }

    private Optional<Pos> findNearestRepellent(ChunkLevel level, Hoglin hoglin) {
        Pos here = hoglin.blockPosition();
        Pos[] found = new Pos[1];
        BlockPosIter.withinManhattan(here.x(), here.y(), here.z(), 8, 4, 8, (x, y, z) -> {
            if (B.isTag(level.getBlockState(new Pos(x, y, z)), "minecraft:hoglin_repellents")) {
                found[0] = new Pos(x, y, z);
                return false;
            }
            return true;
        });
        return Optional.ofNullable(found[0]);
    }
}
