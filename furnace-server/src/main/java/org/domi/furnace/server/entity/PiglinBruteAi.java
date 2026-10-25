// 바닐라 PiglinBruteAi: 피글린 브루트 브레인 — core/idle/fight. 보이는 플레이어(12칸 안)·숙적을 공격하고, 집(HOME) 근처를 지킨다.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.GlobalPos;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.Sensor;

public final class PiglinBruteAi {
    private static final int ANGER_DURATION = 600;
    private static final int MELEE_ATTACK_COOLDOWN = 20;
    private static final double ACTIVITY_SOUND_LIKELIHOOD_PER_TICK = 0.0125;
    private static final int MAX_LOOK_DIST = 8;
    private static final int INTERACTION_RANGE = 8;
    private static final double TARGETING_RANGE = 12.0;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 0.6F;
    private static final int HOME_CLOSE_ENOUGH_DISTANCE = 2;
    private static final int HOME_TOO_FAR_DISTANCE = 100;
    private static final int HOME_STROLL_AROUND_DISTANCE = 5;

    private PiglinBruteAi() {}

    static Brain<PiglinBrute> makeBrain(PiglinBrute brute, Brain<PiglinBrute> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initFightActivity(brute, brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    static void initMemories(PiglinBrute brute) {
        brute.getBrain().setMemory(MemoryModuleType.HOME, new GlobalPos(brute.level().dimension(), brute.blockPosition()));
    }

    private static Map.Entry<BehaviorControl<? super PiglinBrute>, Integer> w(BehaviorControl<? super PiglinBrute> b, int weight) { return Map.entry(b, weight); }

    private static void initCoreActivity(Brain<PiglinBrute> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new LookAtTargetSink(45, 90), new MoveToTargetSink(), InteractWithDoor.create(), StopBeingAngryIfTargetDead.<PiglinBrute>create()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<PiglinBrute> brain) {
        brain.addActivity(Activity.IDLE, 10, List.of(StartAttacking.<PiglinBrute>create(PiglinBruteAi::findNearestValidAttackTarget), createIdleLookBehaviors(), createIdleMovementBehaviors(),
                SetLookAndInteract.create(Targetable::isPlayer, 4)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initFightActivity(PiglinBrute brute, Brain<PiglinBrute> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 10, List.of(StopAttackingIfTargetInvalid.<PiglinBrute>create(t -> !isNearestValidAttackTarget(brute, t)),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(1.0F), MeleeAttack.create(MELEE_ATTACK_COOLDOWN)), MemoryModuleType.ATTACK_TARGET);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RunOne<PiglinBrute> createIdleLookBehaviors() {
        return new RunOne<PiglinBrute>(List.<Map.Entry<? extends BehaviorControl<? super PiglinBrute>, Integer>>of(w(SetEntityLookTarget.createPlayers(MAX_LOOK_DIST), 1),
                w(SetEntityLookTarget.create(t -> t instanceof Entity e && e.type() == EntityType.PIGLIN, MAX_LOOK_DIST), 1),
                w(SetEntityLookTarget.create(t -> t instanceof Entity e && e.type() == EntityType.PIGLIN_BRUTE, MAX_LOOK_DIST), 1), w(SetEntityLookTarget.create(t -> true, MAX_LOOK_DIST), 1),
                w(new DoNothing(30, 60), 1)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RunOne<PiglinBrute> createIdleMovementBehaviors() {
        return new RunOne<PiglinBrute>(List.<Map.Entry<? extends BehaviorControl<? super PiglinBrute>, Integer>>of(w(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 2),
                w(InteractWith.of(EntityType.PIGLIN, INTERACTION_RANGE, MemoryModuleType.INTERACTION_TARGET, SPEED_MULTIPLIER_WHEN_IDLING, 2), 2),
                w(InteractWith.of(EntityType.PIGLIN_BRUTE, INTERACTION_RANGE, MemoryModuleType.INTERACTION_TARGET, SPEED_MULTIPLIER_WHEN_IDLING, 2), 2),
                w(StrollToPoi.create(MemoryModuleType.HOME, SPEED_MULTIPLIER_WHEN_IDLING, HOME_CLOSE_ENOUGH_DISTANCE, HOME_TOO_FAR_DISTANCE), 2),
                w(StrollAroundPoi.create(MemoryModuleType.HOME, SPEED_MULTIPLIER_WHEN_IDLING, HOME_STROLL_AROUND_DISTANCE), 2), w(new DoNothing(30, 60), 1)));
    }

    static void updateActivity(PiglinBrute brute) {
        Brain<PiglinBrute> brain = brute.bruteBrain();
        Activity before = brain.getActiveNonCoreActivity().orElse(null);
        brain.setActiveActivityToFirstValid(List.of(Activity.FIGHT, Activity.IDLE));
        Activity after = brain.getActiveNonCoreActivity().orElse(null);
        if (before != after) playActivitySound(brute);
        brute.setAggressive(brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET));
    }

    private static boolean isNearestValidAttackTarget(AbstractPiglin piglin, Targetable target) { return findNearestValidAttackTarget(piglin).filter(t -> t == target).isPresent(); }

    private static Optional<? extends Targetable> findNearestValidAttackTarget(AbstractPiglin piglin) {
        Optional<Targetable> angry = BehaviorUtils.getLivingEntityFromUUIDMemory(piglin, MemoryModuleType.ANGRY_AT);
        if (angry.isPresent() && Sensor.isEntityAttackableIgnoringLineOfSight(piglin, angry.get())) return angry;
        Optional<? extends Targetable> player = piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_ATTACKABLE_PLAYER).filter(t -> BehaviorUtils.distSqr(piglin, t) <= TARGETING_RANGE * TARGETING_RANGE);
        return player.isPresent() ? player : piglin.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_NEMESIS);
    }

    static void wasHurtBy(PiglinBrute brute, Targetable attacker) {
        if (!(attacker instanceof AbstractPiglin)) PiglinAi.maybeRetaliate(brute, attacker);
    }

    static void maybePlayActivitySound(PiglinBrute brute) {
        if (brute.level().random().nextFloat() < ACTIVITY_SOUND_LIKELIHOOD_PER_TICK) playActivitySound(brute);
    }

    private static void playActivitySound(PiglinBrute brute) {
        brute.bruteBrain().getActiveNonCoreActivity().ifPresent(a -> {
            if (a == Activity.FIGHT) brute.playAngrySound();
        });
    }
}
