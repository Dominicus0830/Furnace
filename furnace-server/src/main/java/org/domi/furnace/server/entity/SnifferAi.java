// 바닐라 SnifferAi: 냄새 맡는 동물의 브레인 구성 — core/idle/sniff/dig 활동과 냄새 맡기·땅 파기 행동들.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;
import org.domi.furnace.server.entity.ai.brain.WalkTarget;

public final class SnifferAi {
    static final int SNIFFING_COOLDOWN_TICKS = 9600;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING = 1.0F;
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_SNIFFING = 1.25F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;

    private SnifferAi() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Sniffer> makeBrain(Brain<Sniffer> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initSniffingActivity(brain);
        initDigActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    static Sniffer resetSniffer(Sniffer sniffer) {
        sniffer.getBrain().eraseMemory(MemoryModuleType.SNIFFER_DIGGING);
        sniffer.getBrain().eraseMemory(MemoryModuleType.SNIFFER_SNIFFING_TARGET);
        return sniffer.transitionTo(Sniffer.State.IDLING);
    }

    private static Map.Entry<Integer, BehaviorControl<? super Sniffer>> p(int prio, BehaviorControl<? super Sniffer> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Sniffer>, Integer> w(BehaviorControl<? super Sniffer> b, int weight) { return Map.entry(b, weight); }

    private static void initCoreActivity(Brain<Sniffer> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new Swim(0.8F), new AnimalPanic(SPEED_MULTIPLIER_WHEN_PANICKING) {
            @Override
            protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) {
                resetSniffer((Sniffer) mob);
                super.start(level, mob, gameTime);
            }
        }, new MoveToTargetSink(500, 700), new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Sniffer> brain) {
        brain.addActivityWithConditions(Activity.IDLE, List.of(
                p(0, new AnimalMakeLove(EntityType.SNIFFER) {
                    @Override
                    protected void start(ChunkLevel level, Animal animal, long gameTime) {
                        resetSniffer((Sniffer) animal);
                        super.start(level, animal, gameTime);
                    }
                }),
                p(1, new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED, m -> m.isBaby() ? 2.5 : 3.5) {
                    @Override
                    protected void start(ChunkLevel level, PathfinderMob mob, long gameTime) {
                        resetSniffer((Sniffer) mob);
                        super.start(level, mob, gameTime);
                    }
                }),
                p(2, new LookAtTargetSink(45, 90)),
                p(3, new FeelingHappy(40, 100)),
                p(4, new RunOne<Sniffer>(List.<Map.Entry<? extends BehaviorControl<? super Sniffer>, Integer>>of(
                        w(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING, 3), 2), w(new Scenting(40, 80), 1), w(new Sniffing(40, 80), 1),
                        w(SetEntityLookTarget.createPlayers(6.0F), 1), w(RandomStroll.stroll(SPEED_MULTIPLIER_WHEN_IDLING), 1), w(new DoNothing(5, 20), 2))))),
                Set.of(Map.entry(MemoryModuleType.SNIFFER_DIGGING, MemoryStatus.VALUE_ABSENT)));
    }

    private static void initSniffingActivity(Brain<Sniffer> brain) {
        brain.addActivityWithConditions(Activity.SNIFF, List.of(p(0, new Searching())),
                Set.of(Map.entry(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.SNIFFER_SNIFFING_TARGET, MemoryStatus.VALUE_PRESENT),
                        Map.entry(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT)));
    }

    private static void initDigActivity(Brain<Sniffer> brain) {
        brain.addActivityWithConditions(Activity.DIG, List.of(p(0, new Digging(160, 180)), p(0, new FinishedDigging(40))),
                Set.of(Map.entry(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT), Map.entry(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT),
                        Map.entry(MemoryModuleType.SNIFFER_DIGGING, MemoryStatus.VALUE_PRESENT)));
    }

    static void updateActivity(Sniffer sniffer) { sniffer.snifferBrain().setActiveActivityToFirstValid(List.of(Activity.DIG, Activity.SNIFF, Activity.IDLE)); }

    /** 파기 시작: 땅 파는 자세로 160~180틱 동안 판다. 다 파면 쿨다운을 건다. */
    static class Digging extends Behavior<Sniffer> {
        Digging(int min, int max) {
            super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_DIGGING, MemoryStatus.VALUE_PRESENT,
                    MemoryModuleType.SNIFF_COOLDOWN, MemoryStatus.VALUE_ABSENT), min, max);
        }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Sniffer s) { return s.canSniff(); }

        @Override
        protected boolean canStillUse(ChunkLevel level, Sniffer s, long gameTime) {
            return s.getBrain().getMemory(MemoryModuleType.SNIFFER_DIGGING).isPresent() && s.canDig() && !s.isInLove();
        }

        @Override protected void start(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.DIGGING); }

        @Override
        protected void stop(ChunkLevel level, Sniffer s, long gameTime) {
            if (timedOut(gameTime)) s.getBrain().setMemoryWithExpiry(MemoryModuleType.SNIFF_COOLDOWN, org.domi.furnace.server.entity.ai.brain.Unit.INSTANCE, SNIFFING_COOLDOWN_TICKS);
            else resetSniffer(s);
        }
    }

    /** 파기를 마칠 때: 솟아오르며 씨앗을 떨구고 기쁨 상태로. */
    static class FinishedDigging extends Behavior<Sniffer> {
        FinishedDigging(int duration) {
            super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT, MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_DIGGING, MemoryStatus.VALUE_PRESENT,
                    MemoryModuleType.SNIFF_COOLDOWN, MemoryStatus.VALUE_PRESENT), duration, duration);
        }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Sniffer s) { return true; }

        @Override protected boolean canStillUse(ChunkLevel level, Sniffer s, long gameTime) { return s.getBrain().getMemory(MemoryModuleType.SNIFFER_DIGGING).isPresent(); }

        @Override protected void start(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.RISING); }

        @Override
        protected void stop(ChunkLevel level, Sniffer s, long gameTime) {
            boolean done = timedOut(gameTime);
            s.transitionTo(Sniffer.State.IDLING).onDiggingComplete(done);
            s.getBrain().eraseMemory(MemoryModuleType.SNIFFER_DIGGING);
            s.getBrain().setMemory(MemoryModuleType.SNIFFER_HAPPY, true);
        }
    }

    static class FeelingHappy extends Behavior<Sniffer> {
        FeelingHappy(int min, int max) { super(Map.of(MemoryModuleType.SNIFFER_HAPPY, MemoryStatus.VALUE_PRESENT), min, max); }

        @Override protected boolean canStillUse(ChunkLevel level, Sniffer s, long gameTime) { return true; }

        @Override protected void start(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.FEELING_HAPPY); }

        @Override
        protected void stop(ChunkLevel level, Sniffer s, long gameTime) {
            s.transitionTo(Sniffer.State.IDLING);
            s.getBrain().eraseMemory(MemoryModuleType.SNIFFER_HAPPY);
        }
    }

    static class Scenting extends Behavior<Sniffer> {
        Scenting(int min, int max) {
            super(Map.of(MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_DIGGING, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_SNIFFING_TARGET,
                    MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_HAPPY, MemoryStatus.VALUE_ABSENT, MemoryModuleType.BREED_TARGET, MemoryStatus.VALUE_ABSENT), min, max);
        }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Sniffer s) { return !s.isTempted(); }

        @Override protected boolean canStillUse(ChunkLevel level, Sniffer s, long gameTime) { return true; }

        @Override protected void start(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.SCENTING); }

        @Override protected void stop(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.IDLING); }
    }

    /** 냄새 맡기: 끝나면 파볼 만한 자리를 찾아 그쪽으로 걷는 목표를 둔다. */
    static class Sniffing extends Behavior<Sniffer> {
        Sniffing(int min, int max) {
            super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_SNIFFING_TARGET, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFF_COOLDOWN,
                    MemoryStatus.VALUE_ABSENT), min, max);
        }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Sniffer s) { return !s.isBaby() && s.canSniff(); }

        @Override protected boolean canStillUse(ChunkLevel level, Sniffer s, long gameTime) { return s.canSniff(); }

        @Override protected void start(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.SNIFFING); }

        @Override
        protected void stop(ChunkLevel level, Sniffer s, long gameTime) {
            boolean done = timedOut(gameTime);
            s.transitionTo(Sniffer.State.IDLING);
            if (done) {
                s.calculateDigPosition().ifPresent(pos -> {
                    s.getBrain().setMemory(MemoryModuleType.SNIFFER_SNIFFING_TARGET, pos);
                    s.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, SPEED_MULTIPLIER_WHEN_SNIFFING, 0));
                });
            }
        }
    }

    /** 찾아가기: 파볼 자리에 도착할 때까지 걸어가다, 닿으면 땅 파기를 표시한다. */
    static class Searching extends Behavior<Sniffer> {
        Searching() {
            super(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT, MemoryModuleType.IS_PANICKING, MemoryStatus.VALUE_ABSENT, MemoryModuleType.SNIFFER_SNIFFING_TARGET,
                    MemoryStatus.VALUE_PRESENT), 600);
        }

        @Override protected boolean checkExtraStartConditions(ChunkLevel level, Sniffer s) { return s.canSniff(); }

        @Override
        protected boolean canStillUse(ChunkLevel level, Sniffer s, long gameTime) {
            if (!s.canSniff()) {
                s.transitionTo(Sniffer.State.IDLING);
                return false;
            }
            Optional<Pos> walk = s.getBrain().getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition());
            Optional<Pos> target = s.getBrain().getMemory(MemoryModuleType.SNIFFER_SNIFFING_TARGET);
            return !walk.isEmpty() && !target.isEmpty() && target.get().equals(walk.get());
        }

        @Override protected void start(ChunkLevel level, Sniffer s, long gameTime) { s.transitionTo(Sniffer.State.SEARCHING); }

        @Override
        protected void stop(ChunkLevel level, Sniffer s, long gameTime) {
            if (s.canDig() && s.canSniff()) s.getBrain().setMemory(MemoryModuleType.SNIFFER_DIGGING, true);
            s.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            s.getBrain().eraseMemory(MemoryModuleType.SNIFFER_SNIFFING_TARGET);
        }
    }
}
