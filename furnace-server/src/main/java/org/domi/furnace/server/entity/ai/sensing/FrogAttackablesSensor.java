// 바닐라 FrogAttackablesSensor: 개구리가 잡아먹을 수 있는 가장 가까운 대상(10칸 안, 도달 못 한 적 없는 것).
package org.domi.furnace.server.entity.ai.sensing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.entity.Frog;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;

public class FrogAttackablesSensor extends NearestVisibleLivingEntitySensor {
    public static final float TARGET_DETECTION_DISTANCE = 10.0F;

    @Override
    protected boolean isMatchingEntity(Mob owner, Targetable target) {
        return !owner.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN) && Sensor_isAttackable(owner, target) && Frog.canEat(target)
                && !isUnreachableAttackTarget(owner, target) && NearestLivingEntitySensor.distSqr(owner, target) <= 100.0;
    }

    private static boolean Sensor_isAttackable(Mob owner, Targetable target) { return org.domi.furnace.server.entity.ai.brain.Sensor.isEntityAttackable(owner, target); }

    private boolean isUnreachableAttackTarget(Mob owner, Targetable target) {
        List<UUID> list = owner.getBrain().getMemory(MemoryModuleType.UNREACHABLE_TONGUE_TARGETS).orElseGet(ArrayList::new);
        return target instanceof org.domi.furnace.server.entity.Entity e && list.contains(e.uuid());
    }

    @Override protected MemoryModuleType<Targetable> getMemory() { return MemoryModuleType.NEAREST_ATTACKABLE; }
}
