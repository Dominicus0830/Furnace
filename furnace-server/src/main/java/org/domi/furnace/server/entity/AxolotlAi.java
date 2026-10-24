// 바닐라 AxolotlAi: 아홀로틀 브레인 구성 — 활동(core/idle/fight/play_dead)과 행동 우선순위.
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

public final class AxolotlAi {
    private static final UniformInt ADULT_FOLLOW_RANGE = UniformInt.of(5, 16);

    private AxolotlAi() {}

    @SuppressWarnings({"unchecked", "rawtypes"})
    static Brain<Axolotl> makeBrain(Brain<Axolotl> brain) {
        initCoreActivity(brain);
        initIdleActivity(brain);
        initFightActivity(brain);
        initPlayDeadActivity(brain);
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    private static Map.Entry<Integer, BehaviorControl<? super Axolotl>> p(int prio, BehaviorControl<? super Axolotl> b) { return Map.entry(prio, b); }

    private static Map.Entry<BehaviorControl<? super Axolotl>, Integer> w(BehaviorControl<? super Axolotl> b, int weight) { return Map.entry(b, weight); }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initPlayDeadActivity(Brain<Axolotl> brain) {
        brain.addActivityAndRemoveMemoriesWhenStopped(Activity.PLAY_DEAD, List.of(p(0, new PlayDead()), p(1, EraseMemoryIf.create((Mob m) -> BehaviorUtils.isBreeding(m), MemoryModuleType.PLAY_DEAD_TICKS))),
                Set.of(Map.entry(MemoryModuleType.PLAY_DEAD_TICKS, MemoryStatus.VALUE_PRESENT)), Set.of(MemoryModuleType.PLAY_DEAD_TICKS));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initFightActivity(Brain<Axolotl> brain) {
        brain.addActivityAndRemoveMemoryWhenStopped(Activity.FIGHT, 0, List.of(StopAttackingIfTargetInvalid.<Axolotl>create(t -> false, Axolotl::onStopAttacking, true),
                SetWalkTargetFromAttackTargetIfTargetOutOfReach.create(AxolotlAi::getSpeedModifierChasing), MeleeAttack.create(20),
                EraseMemoryIf.create((Mob m) -> BehaviorUtils.isBreeding(m), MemoryModuleType.ATTACK_TARGET)), MemoryModuleType.ATTACK_TARGET);
    }

    private static void initCoreActivity(Brain<Axolotl> brain) {
        brain.addActivity(Activity.CORE, 0, List.of(new LookAtTargetSink(45, 90), new MoveToTargetSink(), ValidatePlayDead.create(), new CountDownCooldownTicks(MemoryModuleType.TEMPTATION_COOLDOWN_TICKS)));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void initIdleActivity(Brain<Axolotl> brain) {
        brain.addActivity(Activity.IDLE, List.of(
                p(0, SetEntityLookTargetSometimes.createPlayers(6.0F, UniformInt.of(30, 60))),
                p(1, new AnimalMakeLove(EntityType.AXOLOTL, 0.2F, 2)),
                p(2, new RunOne<Axolotl>(List.<Map.Entry<? extends BehaviorControl<? super Axolotl>, Integer>>of(w(new FollowTemptation(AxolotlAi::getSpeedModifierFollowingAdult), 1),
                        w(BabyFollowAdult.create(ADULT_FOLLOW_RANGE, AxolotlAi::getSpeedModifierFollowingAdult), 1)))),
                p(3, StartAttacking.create((Axolotl a) -> BehaviorUtils.isBreeding(a) ? Optional.<Targetable>empty() : a.getBrain().getMemory(MemoryModuleType.NEAREST_ATTACKABLE))),
                p(3, TryFindWater.create(6, 0.15F)),
                p(4, new GateBehavior<Axolotl>(Map.of(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT), Set.of(), GateBehavior.OrderPolicy.ORDERED, GateBehavior.RunningPolicy.TRY_ALL,
                        List.<Map.Entry<? extends BehaviorControl<? super Axolotl>, Integer>>of(w(RandomStroll.swim(0.5F), 2), w(RandomStroll.stroll(0.15F, false), 2),
                                w(SetWalkTargetFromLookTarget.create(AxolotlAi::canSetWalkTargetFromLookTarget, AxolotlAi::getSpeedModifierWalkTarget, 3), 3),
                                w(OneShot.triggerIf((Mob m) -> m.isInWater()), 5), w(OneShot.triggerIf((Mob m) -> m.onGround()), 5))))));
    }

    private static boolean canSetWalkTargetFromLookTarget(Mob mob) {
        var look = mob.getBrain().getMemory(MemoryModuleType.LOOK_TARGET);
        return look.isPresent() && mob.level().isWaterAt(look.get().currentBlockPosition()) == mob.isInWater();
    }

    public static void updateActivity(Axolotl axolotl) {
        Brain<Axolotl> brain = axolotl.axolotlBrain();
        Activity before = brain.getActiveNonCoreActivity().orElse(null);
        if (before != Activity.PLAY_DEAD) {
            brain.setActiveActivityToFirstValid(List.of(Activity.PLAY_DEAD, Activity.FIGHT, Activity.IDLE));
            if (before == Activity.FIGHT && brain.getActiveNonCoreActivity().orElse(null) != Activity.FIGHT) {
                brain.setMemoryWithExpiry(MemoryModuleType.HAS_HUNTING_COOLDOWN, true, 2400L);
            }
        }
    }

    private static float getSpeedModifierChasing(Mob m) { return m.isInWater() ? 0.6F : 0.15F; }

    private static float getSpeedModifierFollowingAdult(Mob m) { return m.isInWater() ? 0.6F : 0.15F; }

    private static float getSpeedModifierWalkTarget(Mob m) { return m.isInWater() ? 0.5F : 0.15F; }

    public static boolean isFood(ItemStack s) { return org.domi.furnace.server.item.ItemTags.is(s, "minecraft:axolotl_food"); }
}
