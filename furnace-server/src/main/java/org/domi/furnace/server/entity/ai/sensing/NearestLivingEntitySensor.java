// 바닐라 NearestLivingEntitySensor: 주변(16칸) 살아 있는 생물을 가까운 순으로 기억하고, 그중 보이는 것을 고르는 질의 객체도 기억한다.
// (플레이어는 엔티티 저장소에 없어 따로 모아 합친다.)
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.AABB;
import org.domi.furnace.server.entity.LivingEntity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.NearestVisibleLivingEntities;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class NearestLivingEntitySensor<T extends Mob> extends Sensor<T> {
    @Override
    protected void doTick(ChunkLevel level, T entity) {
        AABB box = entity.boundingBox().inflate(radiusXZ(), radiusY(), radiusXZ());
        List<Targetable> list = nearbyLiving(level, box, entity);
        list.sort(Comparator.comparingDouble(t -> distSqr(entity, t)));
        var brain = entity.getBrain();
        brain.setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, list);
        brain.setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES, new NearestVisibleLivingEntities(entity, list));
    }

    /** 상자 안의 살아 있는 생물(자신 제외): 엔티티 저장소 + 플레이어. */
    public static List<Targetable> nearbyLiving(ChunkLevel level, AABB box, Mob self) {
        List<Targetable> out = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != self && x.isAlive())) out.add(e);
        level.forEachPlayerIn(box, p -> {
            if (p.isAlive()) out.add(p);
        });
        return out;
    }

    public static double distSqr(Mob a, Targetable b) {
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        return dx * dx + dy * dy + dz * dz;
    }

    protected int radiusXZ() { return 16; }

    protected int radiusY() { return 16; }

    @Override public Set<MemoryModuleType<?>> requires() { return Set.of(MemoryModuleType.NEAREST_LIVING_ENTITIES, MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES); }
}
