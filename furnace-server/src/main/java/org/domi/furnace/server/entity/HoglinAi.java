// 바닐라 HoglinAi: 호글린 브레인 구성 — idle/fight/avoid 활동. 기피 블록·피글린 수를 보고 도망치거나 맞서 싸운다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public final class HoglinAi {
    public static final int REPELLENT_DETECTION_RANGE_HORIZONTAL = 8;
    public static final int REPELLENT_DETECTION_RANGE_VERTICAL = 4;
    private static final UniformInt RETREAT_DURATION = UniformInt.of(5 * 20, 20 * 20);
    private static final int ATTACK_DURATION = 200;
    private static final int DESIRED_DISTANCE_FROM_PIGLIN_WHEN_IDLING = 8;
    private static final int DESIRED_DISTANCE_FROM_PIGLIN_WHEN_RETREATING = 15;
    private static final int ATTACK_INTERVAL = 40;
    private static final int BABY_ATTACK_INTERVAL = 15;
    private static final int REPELLENT_PACIFY_TIME = 200;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.4F;
    private static final float SPEED_MULTIPLIER_WHEN_RETREATING = 1.3F;
    private static final float SPEED_MULTIPLIER_WHEN_MAKING_LOVE = 0.6F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 0.6F;

    private HoglinAi() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Hoglin> makeBrain(Brain<Hoglin> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new LookAtTargetSink(45, 90), new MoveToTargetSink()));
        initIdleActivity(brain);
        initFightActivity(brain);
        initRetreatActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static Map.Entry<BehaviorControl<? super Hoglin>, Integer> w(BehaviorControl<? super Hoglin> b, int weight) { return Map.entry(b, weight); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Hoglin> brain) {
        brain.addActivity(Activity.IDLE, 10, List.of(
                BecomePassiveIfMemoryPresent.<Hoglin>create(MemoryModuleType.NEAREST_REPELLENT, REPELLENT_PACIFY_TIME),
                new AnimalMakeLove(EntityType.HOGLIN, SPEED_MULTIPLIER_WHEN_MAKING_LOVE, 2),
                SetWalkTargetAwayFrom.pos(MemoryModuleType.NEAREST_REPELLENT, 1.0F, 8, true),
                StartAttacking.<Hoglin>create(HoglinAi::findNearestValidAttackTarget),
                OneShot.sequence(OneShot.triggerIf((Hoglin h) -> h.isAdult()), SetWalkTargetAwayFrom.entity(MemoryModuleType.NEAREST_VISIBLE_ADULT_PIGLIN, SPEED_MULTIPLIER_WHEN_IDLING,
                        DESIRED_DISTANCE_FROM_PIGLIN_WHEN_IDLING, false)),
                SetEntityLookTargetSometimes.create(8.0F, UniformInt.of(30, 60)),
                BabyFollowAdult.create(ADULT_FOLLOW_RANGE, SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT),
                createIdleMovementBehaviors()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initFightActivity(Brain<Hoglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 10, List.of(
                BecomePassiveIfMemoryPresent.<Hoglin>create(MemoryModuleType.NEAREST_REPELLENT, REPELLENT_PACIFY_TIME),
                new AnimalMakeLove(EntityType.HOGLIN, SPEED_MULTIPLIER_WHEN_MAKING_LOVE, 2),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F),
                OneShot.sequence(OneShot.triggerIf((Hoglin h) -> h.isAdult()), MeleeAttack.create(ATTACK_INTERVAL)),
                OneShot.sequence(OneShot.triggerIf((Hoglin h) -> h.isBaby()), MeleeAttack.create(BABY_ATTACK_INTERVAL)),
                StopAttackingIfTargetInvalid.<Hoglin>create(),
                EraseMemoryIf.create((Hoglin h) -> isBreeding(h), MemoryModuleType.ATTACK_TARGET)), MemoryModuleType.ATTACK_TARGET);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initRetreatActivity(Brain<Hoglin> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.AVOID, 10, List.of(
                SetWalkTargetAwayFrom.entity(MemoryModuleType.AVOID_TARGET, SPEED_MULTIPLIER_WHEN_RETREATING, DESIRED_DISTANCE_FROM_PIGLIN_WHEN_RETREATING, false),
                createIdleMovementBehaviors(), SetEntityLookTargetSometimes.create(8.0F, UniformInt.of(30, 60)),
                EraseMemoryIf.create((Hoglin h) -> wantsToStopFleeing(h), MemoryModuleType.AVOID_TARGET)), MemoryModuleType.AVOID_TARGET);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RunOne<Hoglin> createIdleMovementBehaviors() {
        return new RunOne<Hoglin>(List.<Map.Entry<? extends BehaviorControl<? super Hoglin>, Integer>>of(w(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 2),
                w(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3), 2), w(new DoNothing(30, 60), 1)));
    }

    static void updateActivity(Hoglin hoglin) {
        Brain<Hoglin> brain = hoglin.hoglinBrain();
        Activity before = brain.getActiveNonCoreActivity().orElse(null);
        brain.setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.AVOID, Activity.IDLE));
        Activity after = brain.getActiveNonCoreActivity().orElse(null);
        if (before != after) getSoundForCurrentActivity(hoglin).ifPresent(hoglin::makeSound);
        hoglin.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
    }

    static void onHitTarget(Hoglin hoglin, Targetable target) {
        if (!hoglin.isBaby()) {
            if (target instanceof Entity e && e.type() == EntityType.PIGLIN && piglinsOutnumberedByHoglins(hoglin)) {
                setAvoidTarget(hoglin, target);
                groupWithNearbyHoglinsToAvoid(hoglin, target);
            } else {
                broadcastAttackTarget(hoglin, target);
            }
        }
    }

    private static void groupWithNearbyHoglinsToAvoid(Hoglin hoglin, Targetable target) {
        getVisibleAdultHoglins(hoglin).forEach(h -> retreatFromNearestTarget(h, target));
    }

    private static void retreatFromNearestTarget(Hoglin hoglin, Targetable target) {
        var brain = hoglin.hoglinBrain();
        Targetable t = BehaviorUtils.getNearestTarget(hoglin, brain.getMemory(MemoryModuleType.AVOID_TARGET), target);
        t = BehaviorUtils.getNearestTarget(hoglin, brain.getMemory(MemoryModuleType.ATTACK_TARGET), t);
        setAvoidTarget(hoglin, t);
    }

    private static void setAvoidTarget(Hoglin hoglin, Targetable target) {
        var brain = hoglin.hoglinBrain();
        brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.setMemoryWithExpiry(MemoryModuleType.AVOID_TARGET, target, RETREAT_DURATION.sample(hoglin.level().random()));
    }

    private static Optional<Targetable> findNearestValidAttackTarget(Hoglin hoglin) {
        return !isPacified(hoglin) && !isBreeding(hoglin) ? hoglin.hoglinBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER).map(p -> (Targetable) p) : Optional.empty();
    }

    static boolean isPosNearNearestRepellent(Hoglin hoglin, org.domi.furnace.server.block.Pos pos) {
        Optional<org.domi.furnace.server.block.Pos> r = hoglin.hoglinBrain().getMemory(MemoryModuleType.NEAREST_REPELLENT);
        if (r.isEmpty()) return false;
        int dx = r.get().x() - pos.x(), dy = r.get().y() - pos.y(), dz = r.get().z() - pos.z();
        return dx * dx + dy * dy + dz * dz < 64;
    }

    private static boolean wantsToStopFleeing(Hoglin hoglin) { return hoglin.isAdult() && !piglinsOutnumberedByHoglins(hoglin); }

    private static boolean piglinsOutnumberedByHoglins(Hoglin hoglin) {
        if (hoglin.isBaby()) return false;
        int piglins = hoglin.hoglinBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_PIGLIN_COUNT).orElse(0);
        int hoglins = hoglin.hoglinBrain().getMemory(MemoryModuleType.VISIBLE_ADULT_HOGLIN_COUNT).orElse(0) + 1;
        return piglins > hoglins;
    }

    static void onHurtBy(Hoglin hoglin, Targetable attacker) {
        var brain = hoglin.hoglinBrain();
        brain.eraseMemory(MemoryModuleType.PACIFIED);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        if (hoglin.isBaby()) retreatFromNearestTarget(hoglin, attacker);
        else maybeRetaliate(hoglin, attacker);
    }

    private static void maybeRetaliate(Hoglin hoglin, Targetable target) {
        if (!hoglin.hoglinBrain().isActive(Activity.AVOID) || !(target instanceof Entity e && e.type() == EntityType.PIGLIN)) {
            if (!(target instanceof Entity e2 && e2.type() == EntityType.HOGLIN)) {
                if (!BehaviorUtils.isOtherTargetMuchFurtherAwayThanCurrentAttackTarget(hoglin, target, 4.0)) {
                    if (Sensor.isEntityAttackable(hoglin, target)) {
                        setAttackTarget(hoglin, target);
                        broadcastAttackTarget(hoglin, target);
                    }
                }
            }
        }
    }

    private static void setAttackTarget(Hoglin hoglin, Targetable target) {
        var brain = hoglin.hoglinBrain();
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.eraseMemory(MemoryModuleType.BREED_TARGET);
        brain.setMemoryWithExpiry(MemoryModuleType.ATTACK_TARGET, target, ATTACK_DURATION);
    }

    private static void broadcastAttackTarget(Hoglin hoglin, Targetable target) {
        getVisibleAdultHoglins(hoglin).forEach(h -> setAttackTargetIfCloserThanCurrent(h, target));
    }

    private static void setAttackTargetIfCloserThanCurrent(Hoglin hoglin, Targetable target) {
        if (!isBreeding(hoglin)) {
            Targetable t = BehaviorUtils.getNearestTarget(hoglin, hoglin.hoglinBrain().getMemory(MemoryModuleType.ATTACK_TARGET), target);
            setAttackTarget(hoglin, t);
        }
    }

    static Optional<String> getSoundForCurrentActivity(Hoglin hoglin) {
        return hoglin.hoglinBrain().getActiveNonCoreActivity().map(a -> getSoundForActivity(hoglin, a));
    }

    private static String getSoundForActivity(Hoglin hoglin, Activity activity) {
        if (activity == Activity.AVOID || hoglin.isConverting()) return "entity.hoglin.retreat";
        if (activity == Activity.FIGHT) return "entity.hoglin.angry";
        return isNearRepellent(hoglin) ? "entity.hoglin.retreat" : "entity.hoglin.ambient";
    }

    private static List<Hoglin> getVisibleAdultHoglins(Hoglin hoglin) {
        return hoglin.hoglinBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ADULT_HOGLINS).map(l -> l.stream().map(m -> (Hoglin) m).toList()).orElse(List.of());
    }

    private static boolean isNearRepellent(Hoglin hoglin) { return hoglin.hoglinBrain().hasMemoryValue(MemoryModuleType.NEAREST_REPELLENT); }

    private static boolean isBreeding(Hoglin hoglin) { return hoglin.hoglinBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET); }

    static boolean isPacified(Hoglin hoglin) { return hoglin.hoglinBrain().hasMemoryValue(MemoryModuleType.PACIFIED); }
}
