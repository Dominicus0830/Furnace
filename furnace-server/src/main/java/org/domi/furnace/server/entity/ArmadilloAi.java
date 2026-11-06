// 바닐라 ArmadilloAi: 아르마딜로 브레인 구성 — core/idle/panic 활동과 몸을 마는 행동.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public final class ArmadilloAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT = 1.25F;
    private static final double DEFAULT_CLOSE_ENOUGH_DIST = 2.0;
    private static final double BABY_CLOSE_ENOUGH_DIST = 1.0;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);

    private ArmadilloAi() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Armadillo> makeBrain(Brain<Armadillo> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initScaredActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static Map.Entry<Integer, BehaviorControl<? super Armadillo>> p(int prio, BehaviorControl<? super Armadillo> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Armadillo>, Integer> w(BehaviorControl<? super Armadillo> b, int weight) { return Map.entry(b, weight); }

    /** 위험 기억이 없는데 아직 말려 있으면 몸을 푼다. */
    private static OneShot<Armadillo> unrollWhenSafe() {
        return new OneShot<>() {
            @Override
            protected boolean trigger(ChunkLevel level, Armadillo a, long gameTime) {
                if (!absent(a, MemoryModuleType.DANGER_DETECTED_RECENTLY)) return false;
                if (a.isScared()) {
                    a.rollOut();
                    return true;
                }
                return false;
            }
        };
    }

    private static void initCoreActivity(Brain<Armadillo> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new Swim(0.8F), new ArmadilloPanic(SPEED_MULTIPLIER_WHEN_PANICKING), new LookAtTargetSink(45, 90), new MoveToTargetSink() {
            @Override
            protected boolean checkExtraStartConditions(ChunkLevel level, org.domi.furnace.server.entity.Mob mob) {
                return mob instanceof Armadillo a && a.isScared() ? false : super.checkExtraStartConditions(level, mob);
            }
        }, new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS), new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS), unrollWhenSafe()));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Armadillo> brain) {
        brain.addActivity(Activity.IDLE, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(1, new AnimalMakeLove(EntityType.ARMADILLO, 1.0F, 1)),
                p(2, new RunOne<Armadillo>(List.<Map.Entry<? extends BehaviorControl<? super Armadillo>, Integer>>of(
                        w(new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED, m -> m.isBaby() ? BABY_CLOSE_ENOUGH_DIST : DEFAULT_CLOSE_ENOUGH_DIST), 1),
                        w(BabyFollowAdult.create(ADULT_FOLLOW_RANGE, SPEED_MULTIPLIER_WHEN_FOLLOWING_ADULT), 1)))),
                p(3, new RandomLookAround(UniformInt.of(150, 250), 30.0F, 0.0F, 0.0F)),
                p(4, new RunOne<Armadillo>(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), List.<Map.Entry<? extends BehaviorControl<? super Armadillo>, Integer>>of(
                        w(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 1), w(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3), 1), w(new DoNothing(30, 60), 1))))));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initScaredActivity(Brain<Armadillo> brain) {
        brain.addActivityWithConditions(Activity.PANIC, List.of(p(0, new ArmadilloBallUp())),
                Set.of(Map.entry(MemoryModuleType.DANGER_DETECTED_RECENTLY, MemoryStatus.VALUE_PRESENT), Map.entry(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT)));
    }

    public static void updateActivity(Armadillo armadillo) { armadillo.armadilloBrain().setActiveActivityToFirstValid(List.of(Activity.PANIC, Activity.IDLE)); }

    /** 환경 피해로 공황이 되면 먼저 몸을 푼다. */
    public static class ArmadilloPanic extends AnimalPanic {
        public ArmadilloPanic(float speed) { super(speed, m -> "minecraft:panic_environmental_causes"); }

        @Override
        protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) {
            ((Armadillo) mob).rollOut();
            super.start(level, mob, gameTime);
        }
    }

    /** 위험이 있는 동안 몸을 말고 있다가, 위험이 멀어지면 가끔 내다보고 풀린다. */
    public static class ArmadilloBallUp extends Behavior<Armadillo> {
        static final int BALL_UP_STAGE_DURATION_TICKS = 5 * 60 * 20;
        static final int DANGER_NEAR_TICKS = 75;
        int nextPeekTimer;
        boolean dangerWasClose;

        public ArmadilloBallUp() { super(Map.of(), BALL_UP_STAGE_DURATION_TICKS); }

        @Override
        protected void tick(ChunkLevel level, Armadillo armadillo, long gameTime) {
            if (nextPeekTimer > 0) nextPeekTimer--;
            if (armadillo.shouldSwitchToScaredState()) {
                armadillo.switchToState(Armadillo.ArmadilloState.SCARED);
                if (armadillo.onGround()) armadillo.playSound("entity.armadillo.land", 1.0F, 1.0F);
            } else {
                Armadillo.ArmadilloState state = armadillo.getState();
                long left = armadillo.getBrain().getTimeUntilExpiry(MemoryModuleType.DANGER_DETECTED_RECENTLY);
                boolean close = left > DANGER_NEAR_TICKS;
                if (close != dangerWasClose) nextPeekTimer = pickNextPeekTimer(armadillo);
                dangerWasClose = close;
                if (state == Armadillo.ArmadilloState.SCARED) {
                    if (nextPeekTimer == 0 && armadillo.onGround() && close) {
                        armadillo.broadcastEntityEvent((byte) 64);
                        nextPeekTimer = pickNextPeekTimer(armadillo);
                    }
                    if (left < Armadillo.ArmadilloState.UNROLLING.animationDuration()) {
                        armadillo.playSound("entity.armadillo.unroll_start", 1.0F, 1.0F);
                        armadillo.switchToState(Armadillo.ArmadilloState.UNROLLING);
                    }
                } else if (state == Armadillo.ArmadilloState.UNROLLING && left > Armadillo.ArmadilloState.UNROLLING.animationDuration()) {
                    armadillo.switchToState(Armadillo.ArmadilloState.SCARED);
                }
            }
        }

        private int pickNextPeekTimer(Armadillo armadillo) { return Armadillo.ArmadilloState.SCARED.animationDuration() + 100 + armadillo.random().nextInt(300); }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Armadillo armadillo) { return armadillo.onGround(); }

        @Override protected boolean canStillUse(ChunkLevel level, Armadillo armadillo, long gameTime) { return armadillo.getState().isThreatened(); }

        @Override protected void start(ChunkLevel level, Armadillo armadillo, long gameTime) { armadillo.rollUp(); }

        @Override
        protected void stop(ChunkLevel level, Armadillo armadillo, long gameTime) {
            if (!armadillo.canStayRolledUp()) armadillo.rollOut();
        }
    }
}
