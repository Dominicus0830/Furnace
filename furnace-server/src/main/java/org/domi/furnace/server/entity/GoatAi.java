// 바닐라 GoatAi: 염소 브레인 구성 — 활동(core/idle/long_jump/ram)과 행동 우선순위.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.targeting.TargetingConditions;

public final class GoatAi {
    static final int RAM_PREPARE_TIME = 20;
    static final int RAM_MAX_DISTANCE = 7;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;
    private static final UniformInt TIME_BETWEEN_LONG_JUMPS = UniformInt.of(600, 1200);
    static final int MAX_LONG_JUMP_HEIGHT = 5;
    static final int MAX_LONG_JUMP_WIDTH = 5;
    static final float MAX_JUMP_VELOCITY_MULTIPLIER = 3.5714288F;
    private static final UniformInt TIME_BETWEEN_RAMS = UniformInt.of(600, 6000);
    private static final UniformInt TIME_BETWEEN_RAMS_SCREAMER = UniformInt.of(100, 300);
    private static final TargetingConditions RAM_TARGET_CONDITIONS = TargetingConditions.forCombat()
            .selector(t -> !(t instanceof Entity e && e.type() == EntityType.GOAT) && t instanceof Entity e2 && e2.level().withinBorder(e2.blockPosition()));
    private static final float SPEED_MULTIPLIER_WHEN_RAMMING = 3.0F;
    static final int RAM_MIN_DISTANCE = 4;
    static final float SPEED_MULTIPLIER_WHEN_PREPARING_TO_RAM = 1.25F;

    private GoatAi() {}

    static void initMemories(Goat goat, org.domi.furnace.server.block.RandomSource random) {
        goat.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, TIME_BETWEEN_LONG_JUMPS.sample(random));
        goat.getBrain().setMemory(MemoryModuleType.RAM_COOLDOWN_TICKS, TIME_BETWEEN_RAMS.sample(random));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Goat> makeBrain(Brain<Goat> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initLongJumpActivity(brain);
        initRamActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static Map.Entry<Integer, BehaviorControl<? super Goat>> p(int prio, BehaviorControl<? super Goat> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Goat>, Integer> w(BehaviorControl<? super Goat> b, int weight) { return Map.entry(b, weight); }

    private static void initCoreActivity(Brain<Goat> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new Swim(0.8F), new AnimalPanic(SPEED_MULTIPLIER_WHEN_PANICKING), new LookAtTargetSink(45, 90), new MoveToTargetSink(),
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS), new CountDownCooldownTicks(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS),
                new CountDownCooldownTicks(MemoryModuleType.RAM_COOLDOWN_TICKS)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Goat> brain) {
        brain.addActivityWithConditions(Activity.IDLE, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(0, new AnimalMakeLove(EntityType.GOAT)),
                p(1, new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED)),
                p(2, BabyFollowAdult.create(ADULT_FOLLOW_RANGE, SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT)),
                p(3, new RunOne<Goat>(List.<Map.Entry<? extends BehaviorControl<? super Goat>, Integer>>of(w(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 2),
                        w(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3), 2), w(new DoNothing(30, 60), 1))))),
                Set.of(Map.entry(MemoryModuleType.RAM_TARGET, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initLongJumpActivity(Brain<Goat> brain) {
        brain.addActivityWithConditions(Activity.LONG_JUMP, List.of(
                p(0, new LongJumpMidJump(TIME_BETWEEN_LONG_JUMPS, "entity.goat.step")),
                p(1, new LongJumpToRandomPos<Goat>(TIME_BETWEEN_LONG_JUMPS, MAX_LONG_JUMP_HEIGHT, MAX_LONG_JUMP_WIDTH, MAX_JUMP_VELOCITY_MULTIPLIER,
                        g -> g.isScreamingGoat() ? "entity.goat.screaming.long_jump" : "entity.goat.long_jump"))),
                Set.of(Map.entry(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT),
                        Map.entry(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initRamActivity(Brain<Goat> brain) {
        brain.addActivityWithConditions(Activity.RAM, List.of(
                p(0, new RamTarget(g -> g.isScreamingGoat() ? TIME_BETWEEN_RAMS_SCREAMER : TIME_BETWEEN_RAMS, RAM_TARGET_CONDITIONS, SPEED_MULTIPLIER_WHEN_RAMMING,
                        g -> g.isBaby() ? 1.0 : 2.5, g -> g.isScreamingGoat() ? "entity.goat.screaming.ram_impact" : "entity.goat.ram_impact",
                        g -> g.isScreamingGoat() ? "entity.goat.screaming.horn_break" : "entity.goat.horn_break")),
                p(1, new PrepareRamNearestTarget<Goat>(g -> g.isScreamingGoat() ? TIME_BETWEEN_RAMS_SCREAMER.min() : TIME_BETWEEN_RAMS.min(), RAM_MIN_DISTANCE, RAM_MAX_DISTANCE,
                        SPEED_MULTIPLIER_WHEN_PREPARING_TO_RAM, RAM_TARGET_CONDITIONS, RAM_PREPARE_TIME, g -> g.isScreamingGoat() ? "entity.goat.screaming.prepare_ram" : "entity.goat.prepare_ram"))),
                Set.of(Map.entry(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT),
                        Map.entry(MemoryModuleType.RAM_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT)));
    }

    public static void updateActivity(Goat goat) {
        goat.goatBrain().setActiveActivityToFirstValid(List.of(Activity.RAM, Activity.LONG_JUMP, Activity.IDLE));
    }
}
