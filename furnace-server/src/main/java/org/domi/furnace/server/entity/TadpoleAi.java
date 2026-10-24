// 바닐라 TadpoleAi: 올챙이 브레인 구성 — 겁먹으면 도망(core), 플레이어를 바라보고 슬라임 볼을 따라가며 헤엄친다(idle).
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.entity.ai.behavior.*;
import org.domi.furnace.server.entity.ai.brain.Activity;
import org.domi.furnace.server.entity.ai.brain.Brain;
import org.domi.furnace.server.entity.ai.brain.MemoryModuleType;
import org.domi.furnace.server.entity.ai.brain.MemoryStatus;

public final class TadpoleAi {
    private static final float SPEED_MULTIPLIER_WHEN_PANICKING = 2.0F;
    private static final float SPEED_MULTIPLIER_WHEN_IDLING_IN_WATER = 0.5F;
    private static final float SPEED_MULTIPLIER_WHEN_TEMPTED = 1.25F;

    private TadpoleAi() {}

    static Brain<Tadpole> makeBrain(Brain<Tadpole> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static void initCoreActivity(Brain<Tadpole> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new AnimalPanic(SPEED_MULTIPLIER_WHEN_PANICKING), new LookAtTargetSink(45, 90), new MoveToTargetSink(),
                new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)));
    }

    private static Map.Entry<Integer, BehaviorControl<? super Tadpole>> p(int prio, BehaviorControl<? super Tadpole> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Tadpole>, Integer> w(BehaviorControl<? super Tadpole> b, int weight) { return Map.entry(b, weight); }

    private static void initIdleActivity(Brain<Tadpole> brain) {
        brain.addActivity(Activity.IDLE, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(1, new FollowTemptation(m -> SPEED_MULTIPLIER_WHEN_TEMPTED)),
                p(2, new GateBehavior<Tadpole>(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), Set.of(), GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.TRY_ALL,
                        List.<Map.Entry<? extends BehaviorControl<? super Tadpole>, Integer>>of(w(RandomStroll.swim(SPEED_MULTIPLIER_WHEN_IDLING_IN_WATER), 2),
                                w(SetWalkTargetFromLookTarget.create(SPEED_MULTIPLIER_WHEN_IDLING_IN_WATER, 3), 3), w(OneShot.triggerIf((Mob m) -> m.isInWater()), 5))))));
    }

    public static void updateActivity(Tadpole tadpole) {
        tadpole.tadpoleBrain().setActiveActivityToFirstValid(List.of(Activity.IDLE));
    }
}
