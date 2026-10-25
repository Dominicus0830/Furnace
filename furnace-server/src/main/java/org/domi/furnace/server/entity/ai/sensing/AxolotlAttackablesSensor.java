// 바닐라 AxolotlAttackablesSensor: 8칸 안 물속의 사냥감(항상 적대 종류, 또는 사냥 쿨다운이 없을 때 사냥 대상) 중 가장 가까운 것.
package org.domi.furnace.server.entity.ai.sensing;

import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public class AxolotlAttackablesSensor extends NearestVisibleLivingEntitySensor {
    public static final float TARGET_DETECTION_DISTANCE = 8.0F;

    @Override
    protected boolean isMatchingEntity(Mob owner, Targetable target) {
        return NearestLivingEntitySensor.distSqr(owner, target) <= 64.0 && inWater(owner, target) && (isAlwaysHostile(target) || isHuntTarget(owner, target))
                && Sensor.isEntityAttackable(owner, target);
    }

    private static boolean inWater(Mob owner, Targetable t) { return t instanceof Entity e ? e.isInWater() : owner.level().isWaterAt(t.blockPosition()); }

    private static boolean isHuntTarget(Mob owner, Targetable t) {
        return !owner.getBrain().hasMemoryValue(MemoryModuleType.HAS_HUNTING_COOLDOWN) && t instanceof Entity e && e.type().is("minecraft:axolotl_hunt_targets");
    }

    private static boolean isAlwaysHostile(Targetable t) { return t instanceof Entity e && e.type().is("minecraft:axolotl_always_hostiles"); }

    @Override protected MemoryModuleType<Targetable> getMemory() { return MemoryModuleType.NEAREST_ATTACKABLE; }
}
