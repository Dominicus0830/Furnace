// 바닐라 VillagerHostilesSensor: 보이는 생물 중 주민이 무서워하는 종류가 종류별 거리 안이면 가장 가까운 것을 NEAREST_HOSTILE 로.
package org.domi.furnace.server.entity.ai.sensing;

import java.util.Map;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.EntityType;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public class VillagerHostilesSensor extends NearestVisibleLivingEntitySensor {
    private static final Map<EntityType, Float> ACCEPTABLE_DISTANCE_FROM_HOSTILES = Map.ofEntries(Map.entry(EntityType.DROWNED, 8.0F),
            Map.entry(EntityType.EVOKER, 12.0F), Map.entry(EntityType.HUSK, 8.0F), Map.entry(EntityType.ILLUSIONER, 12.0F), Map.entry(EntityType.PILLAGER, 15.0F),
            Map.entry(EntityType.RAVAGER, 12.0F), Map.entry(EntityType.VEX, 8.0F), Map.entry(EntityType.VINDICATOR, 10.0F), Map.entry(EntityType.ZOGLIN, 10.0F),
            Map.entry(EntityType.ZOMBIE, 8.0F), Map.entry(EntityType.ZOMBIE_VILLAGER, 8.0F));

    public static boolean isHostile(Targetable t) { return t instanceof Entity e && ACCEPTABLE_DISTANCE_FROM_HOSTILES.containsKey(e.type()); }

    @Override
    protected boolean isMatchingEntity(Mob owner, Targetable t) {
        if (!(t instanceof Entity e)) return false;
        Float d = ACCEPTABLE_DISTANCE_FROM_HOSTILES.get(e.type());
        if (d == null) return false;
        double dx = e.x() - owner.x(), dy = e.y() - owner.y(), dz = e.z() - owner.z();
        return dx * dx + dy * dy + dz * dz <= d * d;
    }

    @Override protected MemoryModuleType<Targetable> getMemory() { return MemoryModuleType.NEAREST_HOSTILE; }
}
