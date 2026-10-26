// 바닐라 SecondaryPoiSensor(40틱): 주변 9×5×9 칸(x → y → z)에서 직업의 보조 작업 블록(농부의 경작지)을 모아 SECONDARY_JOB_SITE 로 둔다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.Villager;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class SecondaryPoiSensor extends Sensor<Villager> {
    public SecondaryPoiSensor() { super(40); }

    @Override
    protected void doTick(ChunkLevel level, Villager v) {
        Set<String> blocks = Villager.secondaryPoi(v.profession());
        Pos p = v.blockPosition();
        List<GlobalPos> list = new ArrayList<>();
        for (int dx = -4; dx <= 4; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -4; dz <= 4; dz++) {
                    Pos q = new Pos(p.x() + dx, p.y() + dy, p.z() + dz);
                    if (!blocks.isEmpty() && blocks.contains(B.BLOCKS.nameOfState(level.getBlockState(q)))) list.add(new GlobalPos(level.dimension(), q));
                }
            }
        }
        if (!list.isEmpty()) v.getBrain().setMemory(MemoryModuleType.SECONDARY_JOB_SITE, list);
        else v.getBrain().eraseMemory(MemoryModuleType.SECONDARY_JOB_SITE);
    }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.SECONDARY_JOB_SITE); }
}
