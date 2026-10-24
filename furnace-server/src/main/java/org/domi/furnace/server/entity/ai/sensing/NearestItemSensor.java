// 바닐라 NearestItemSensor: 줍고 싶은 가장 가까운 아이템(32칸, 시선이 닿는 것)을 기억한다.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class NearestItemSensor extends Sensor<Mob> {
    public static final int MAX_DISTANCE_TO_WANTED_ITEM = 32;

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM); }

    @Override
    protected void doTick(ChunkLevel level, Mob mob) {
        List<ItemEntity> list = level.getEntitiesOfClass(ItemEntity.class, mob.boundingBox().inflate(32.0, 16.0, 32.0), x -> true);
        list.sort(Comparator.comparingDouble(e -> {
            double dx = e.x() - mob.x(), dy = e.y() - mob.y(), dz = e.z() - mob.z();
            return dx * dx + dy * dy + dz * dz;
        }));
        Optional<ItemEntity> best = list.stream().filter(e -> mob.wantsToPickUp(e.getItem())).filter(e -> {
            double dx = e.x() - mob.x(), dy = e.y() - mob.y(), dz = e.z() - mob.z();
            return dx * dx + dy * dy + dz * dz <= 32.0 * 32.0;
        }).filter(mob::hasLineOfSightToEntity).findFirst();
        mob.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM, best);
    }
}
