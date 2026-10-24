// 바닐라 ScareDetected 센서(아르마딜로): 가까이 있는 무서운 생물이 보이면 DANGER_DETECTED_RECENTLY 를 80틱 둔다. 5틱마다 훑는다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Armadillo;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class ArmadilloScareDetectedSensor extends Sensor<Armadillo> {
    public ArmadilloScareDetectedSensor() { super(5); }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES); }

    @Override
    protected void doTick(ChunkLevel level, Armadillo mob) {
        var brain = mob.getBrain();
        if (!mob.canStayRolledUp()) {
            brain.eraseMemory(MemoryModuleType.DANGER_DETECTED_RECENTLY);
            return;
        }
        var nearby = brain.getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
        if (nearby.isEmpty()) return;
        List<org.domi.furnace.server.entity.Targetable> list = nearby.get();
        if (list.stream().anyMatch(mob::isScaredBy)) brain.setMemoryWithExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY, true, 80L);
    }
}
