// 바닐라 FrogAi: 개구리 브레인 구성 — 활동(core/idle/swim/lay_spawn/long_jump/tongue)과 행동 우선순위.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.navigation.PathType;
import org.domi.furnace.server.entity.ai.navigation.PathfindingContext;
import org.domi.furnace.server.entity.ai.navigation.WalkNodeEvaluator;

public final class FrogAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_ON_LAND = 1.0F;
    private static final float SPEED_MULTIPLIER_IN_WATER = 0.75F;
    private static final UniformInt TIME_BETWEEN_LONG_JUMPS = UniformInt.of(100, 140);
    private static final int MAX_LONG_JUMP_HEIGHT = 2;
    private static final int MAX_LONG_JUMP_WIDTH = 4;
    private static final float MAX_JUMP_VELOCITY_MULTIPLIER = 3.5714288F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;

    private FrogAi() {}

    static void initMemories(Frog frog, org.domi.furnace.server.block.RandomSource random) {
        frog.getBrain().setMemory(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, TIME_BETWEEN_LONG_JUMPS.sample(random));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Frog> makeBrain(Brain<Frog> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initSwimActivity(brain);
        initLaySpawnActivity(brain);
        initTongueActivity(brain);
        initJumpActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Frog> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new AnimalPanic(SPEED_MULTIPLIER_WHEN_PANICKING), new LookAtTargetSink(45, 90), new MoveToTargetSink(),
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS), new CountDownCooldownTicks(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS)));
    }

    private static Map.Entry<Integer, BehaviorControl<? super Frog>> p(int prio, BehaviorControl<? super Frog> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Frog>, Integer> w(BehaviorControl<? super Frog> b, int weight) { return Map.entry(b, weight); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Frog> brain) {
        brain.addActivityWithConditions(Activity.IDLE, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(0, new AnimalMakeLove(EntityType.FROG)),
                p(1, new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED)),
                p(2, StartAttacking.create(FrogAi::canAttack, m -> m.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE))),
                p(3, TryFindLand.create(6, SPEED_MULTIPLIER_ON_LAND)),
                p(4, new RunOne<Frog>(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        List.<Map.Entry<? extends BehaviorControl<? super Frog>, Integer>>of(w(RandomStroll.stroll(1.0F), 1), w(SetWalkTargetFromLookTarget.create(1.0F, 3), 1), w(new Croak(), 3), w(OneShot.triggerIf((Mob m) -> m.onGround()), 2))))),
                Set.of(Map.entry(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.IS_IN_WATER, MemoryStatus.VALUE_ABSENT)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initSwimActivity(Brain<Frog> brain) {
        brain.addActivityWithConditions(Activity.SWIM, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(1, new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED)),
                p(2, StartAttacking.create(FrogAi::canAttack, m -> m.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE))),
                p(3, TryFindLand.create(8, 1.5F)),
                p(5, new GateBehavior<Frog>(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), Set.of(), GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.TRY_ALL,
                        List.<Map.Entry<? extends BehaviorControl<? super Frog>, Integer>>of(w(RandomStroll.swim(SPEED_MULTIPLIER_IN_WATER), 1), w(RandomStroll.stroll(1.0F, true), 1), w(SetWalkTargetFromLookTarget.create(1.0F, 3), 1),
                                w(OneShot.triggerIf((Mob m) -> m.isInWater()), 5))))),
                Set.of(Map.entry(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.IS_IN_WATER, MemoryStatus.VALUE_PRESENT)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initLaySpawnActivity(Brain<Frog> brain) {
        brain.addActivityWithConditions(Activity.LAY_SPAWN, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(1, StartAttacking.create(FrogAi::canAttack, m -> m.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE))),
                p(2, TryFindLandNearWater.create(8, SPEED_MULTIPLIER_ON_LAND)),
                p(3, TryLaySpawnOnWaterNearLand.create("minecraft:frogspawn")),
                p(4, new RunOne<Frog>(List.<Map.Entry<? extends BehaviorControl<? super Frog>, Integer>>of(w(RandomStroll.stroll(1.0F), 2), w(SetWalkTargetFromLookTarget.create(1.0F, 3), 1), w(new Croak(), 2), w(OneShot.triggerIf((Mob m) -> m.onGround()), 1))))),
                Set.of(Map.entry(MemoryModuleType.LONG_JUMP_MID_JUMP, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.IS_PREGNANT, MemoryStatus.VALUE_PRESENT)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initJumpActivity(Brain<Frog> brain) {
        brain.addActivityWithConditions(Activity.LONG_JUMP, List.of(
                p(0, new LongJumpMidJump(TIME_BETWEEN_LONG_JUMPS, "entity.frog.step")),
                p(1, new LongJumpToPreferredBlock<Frog>(TIME_BETWEEN_LONG_JUMPS, MAX_LONG_JUMP_HEIGHT, MAX_LONG_JUMP_WIDTH, MAX_JUMP_VELOCITY_MULTIPLIER, m -> "entity.frog.long_jump",
                        "minecraft:frog_prefer_jump_to", 0.5F, FrogAi::isAcceptableLandingPosition))),
                Set.of(Map.entry(MemoryModuleType.TEMPTING_PLAYER, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT),
                        Map.entry(MemoryModuleType.LONG_JUMP_COOLDOWN_TICKS, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.IS_IN_WATER, MemoryStatus.VALUE_ABSENT)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initTongueActivity(Brain<Frog> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.TONGUE, 0, List.of(StopAttackingIfTargetInvalid.create(), new ShootTongue("entity.frog.tongue", "entity.frog.eat")),
                MemoryModuleType.ATTACK_TARGET);
    }

    private static <E extends Mob> boolean isAcceptableLandingPosition(E mob, Pos pos) {
        ChunkLevel level = mob.level();
        Pos above = pos.above();
        if (level.getFluidState(pos) == 0 && level.getFluidState(above) == 0 && level.getFluidState(pos.below()) == 0) {
            int state = level.getBlockState(pos), stateAbove = level.getBlockState(above);
            if (!B.isTag(state, "minecraft:frog_prefer_jump_to") && !B.isTag(stateAbove, "minecraft:frog_prefer_jump_to")) {
                PathfindingContext ctx = new PathfindingContext(level, mob);
                PathType t1 = WalkNodeEvaluator.getPathTypeStatic(ctx, pos);
                PathType t2 = WalkNodeEvaluator.getPathTypeStatic(ctx, above);
                return t1 != PathType.TRAPDOOR && (!B.isAir(state) || t2 != PathType.TRAPDOOR) ? LongJumpToRandomPos.defaultAcceptableLandingSpot((PathfinderMob) mob, pos) : true;
            }
            return true;
        }
        return false;
    }

    private static boolean canAttack(Frog frog) { return !frog.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET); }

    public static void updateActivity(Frog frog) {
        frog.getBrain().setActiveActivityToFirstValid(List.of(Activity.TONGUE, Activity.LAY_SPAWN, Activity.LONG_JUMP, Activity.SWIM, Activity.IDLE));
    }
}
