// 바닐라 Sensor: scanRate 틱마다 주변을 훑어 브레인 기억을 채운다. 시작 시점은 몹마다 무작위로 어긋난다(월드 난수와 무관).
package org.domi.furnace.server.entity.ai.brain;

import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.Mob;
import org.domi.furnace.server.entity.Targetable;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public abstract class Sensor<E extends Mob> {
    // region 스레드 여럿이 함께 쓰는 정적 난수 — 바닐라 createThreadSafe.
    private static final org.domi.furnace.server.block.RandomSource RANDOM = org.domi.furnace.server.block.RandomSource.createThreadSafeFor(
            org.domi.furnace.server.block.RandomSeeds.key("Sensor.RANDOM"), org.domi.furnace.server.block.RandomSeeds.Purpose.SENSOR_SCAN);
    private static final int DEFAULT_SCAN_RATE = 20;
    protected static final int TARGETING_RANGE = 16;
    private static final TargetingConditions TARGET_CONDITIONS = TargetingConditions.forNonCombat().range(16.0);
    private static final TargetingConditions TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forNonCombat().range(16.0).ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS = TargetingConditions.forCombat().range(16.0);
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING = TargetingConditions.forCombat().range(16.0).ignoreInvisibilityTesting();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT = TargetingConditions.forCombat().range(16.0).ignoreLineOfSight();
    private static final TargetingConditions ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT =
            TargetingConditions.forCombat().range(16.0).ignoreLineOfSight().ignoreInvisibilityTesting();

    private final int scanRate;
    private long timeToTick;

    public Sensor(int scanRate) {
        this.scanRate = scanRate;
        this.timeToTick = RANDOM.nextInt(scanRate);
    }

    public Sensor() { this(DEFAULT_SCAN_RATE); }

    public final void tick(ChunkLevel level, E entity) {
        if (--timeToTick <= 0L) {
            timeToTick = scanRate;
            doTick(level, entity);
        }
    }

    protected abstract void doTick(ChunkLevel level, E entity);

    public abstract Set<MemoryModuleType<?>> requires();

    public static boolean isEntityTargetable(Mob owner, Targetable target) {
        return owner.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
                ? TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(owner, target) : TARGET_CONDITIONS.test(owner, target);
    }

    public static boolean isEntityAttackable(Mob owner, Targetable target) {
        return owner.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
                ? ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_TESTING.test(owner, target) : ATTACK_TARGET_CONDITIONS.test(owner, target);
    }

    public static boolean isEntityAttackableIgnoringLineOfSight(Mob owner, Targetable target) {
        return owner.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
                ? ATTACK_TARGET_CONDITIONS_IGNORE_INVISIBILITY_AND_LINE_OF_SIGHT.test(owner, target) : ATTACK_TARGET_CONDITIONS_IGNORE_LINE_OF_SIGHT.test(owner, target);
    }
}
