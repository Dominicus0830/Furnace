// 바닐라 CamelAi: 낙타 브레인 구성 — core/idle 활동과 앉기·일어서기 행동.
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

public final class CamelAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 4.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 2.5F;
    private static final float SPEED_MULTIPLIER_FOLLOWING_ADULT = 2.5F;
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);

    private CamelAi() {}

    static void initMemories(Camel camel, org.domi.furnace.server.block.RandomSource random) { }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Camel> makeBrain(Brain<Camel> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static Map.Entry<Integer, BehaviorControl<? super Camel>> p(int prio, BehaviorControl<? super Camel> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Camel>, Integer> w(BehaviorControl<? super Camel> b, int weight) { return Map.entry(b, weight); }

    private static void initCoreActivity(Brain<Camel> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new Swim(0.8F), new CamelPanic(SPEED_MULTIPLIER_WHEN_PANICKING), new LookAtTargetSink(45, 90), new MoveToTargetSink(),
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS), new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Camel> brain) {
        brain.addActivity(Activity.IDLE, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(1, new AnimalMakeLove(EntityType.CAMEL)),
                p(2, new RunOne<Camel>(List.<Map.Entry<? extends BehaviorControl<? super Camel>, Integer>>of(
                        w(new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED, m -> m.isBaby() ? 2.5 : 3.5), 1),
                        w(OneShot.sequence(OneShot.triggerIf((Camel c) -> !c.refuseToMove()), BabyFollowAdult.create(ADULT_FOLLOW_RANGE, SPEED_MULTIPLIER_FOLLOWING_ADULT)), 1)))),
                p(3, new RandomLookAround(UniformInt.of(150, 250), 30.0F, 0.0F, 0.0F)),
                p(4, new RunOne<Camel>(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), List.<Map.Entry<? extends BehaviorControl<? super Camel>, Integer>>of(
                        w(OneShot.sequence(OneShot.triggerIf((Camel c) -> !c.refuseToMove()), RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING)), 1),
                        w(OneShot.sequence(OneShot.triggerIf((Camel c) -> !c.refuseToMove()), SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3)), 1),
                        w(new RandomSitting(20), 1), w(new DoNothing(30, 60), 1))))));
    }

    public static void updateActivity(Camel camel) { camel.camelBrain().setActiveActivityToFirstValid(List.of(Activity.IDLE)); }

    /** 공황이 시작되면 앉아 있어도 바로 일어선다. */
    public static class CamelPanic extends AnimalPanic {
        public CamelPanic(float speed) { super(speed); }

        @Override
        protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) {
            ((Camel) mob).standUpInstantly();
            super.start(level, mob, gameTime);
        }
    }

    /** 서 있을 만큼 서 있었으면 앉고, 앉아 있으면 일어선다. */
    public static class RandomSitting extends Behavior<Camel> {
        private final int minimalPoseTicks;

        public RandomSitting(int minimalSittingSeconds) {
            super(Map.of());
            this.minimalPoseTicks = minimalSittingSeconds * 20;
        }

        @Override
        protected boolean checkExtraStartConditions(ChunkLevel level, Camel camel) {
            return !camel.isInWater() && camel.getPoseTime() >= minimalPoseTicks && camel.onGround() && camel.canChangePose();
        }

        @Override
        protected void start(ChunkLevel level, Camel camel, long gameTime) {
            if (camel.isCamelSitting()) camel.standUp();
            else if (!camel.isPanicking()) camel.sitDown();
        }
    }
}
