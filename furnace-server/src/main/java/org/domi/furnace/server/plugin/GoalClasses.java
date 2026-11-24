// 서버 목표 클래스 → 가장 가까운 바닐라 목표 클래스 이름(몹 목표 설계 M1). 호환 계층이 그 이름의 NMS 모양 객체를 만든다
// (instanceof TemptGoal 이 바닐라와 같게). 몹 안쪽 목표(여우 딸기 먹기 등)는 이식한 부모(MoveToBlockGoal…)로 올라간다.
package org.domi.furnace.server.plugin;

import java.util.Map;
import org.domi.furnace.server.entity.ai.goal.*;

final class GoalClasses {

    private GoalClasses() {}

    private static final String P = "net.minecraft.world.entity.ai.goal.";
    private static final String T = P + "target.";

    /** 이식한 목표 클래스(서버 ai.goal 패키지) → 바닐라 이름. 서버 이름이 다른 것(LookAtMobGoal·SitWhenOrderedToDoGoal)도 여기서 맞춘다. */
    private static final Map<Class<?>, String> PORTED = Map.ofEntries(
            Map.entry(AvoidEntityGoal.class, P + "AvoidEntityGoal"),
            Map.entry(BegGoal.class, P + "BegGoal"),
            Map.entry(BreakDoorGoal.class, P + "BreakDoorGoal"),
            Map.entry(BreathAirGoal.class, P + "BreathAirGoal"),
            Map.entry(BreedGoal.class, P + "BreedGoal"),
            Map.entry(ClimbOnTopOfPowderSnowGoal.class, P + "ClimbOnTopOfPowderSnowGoal"),
            Map.entry(DoorInteractGoal.class, P + "DoorInteractGoal"),
            Map.entry(EatBlockGoal.class, P + "EatBlockGoal"),
            Map.entry(FleeSunGoal.class, P + "FleeSunGoal"),
            Map.entry(FloatGoal.class, P + "FloatGoal"),
            Map.entry(FollowFlockLeaderGoal.class, P + "FollowFlockLeaderGoal"),
            Map.entry(FollowMobGoal.class, P + "FollowMobGoal"),
            Map.entry(FollowOwnerGoal.class, P + "FollowOwnerGoal"),
            Map.entry(FollowParentGoal.class, P + "FollowParentGoal"),
            Map.entry(HurtByTargetGoal.class, T + "HurtByTargetGoal"),
            Map.entry(InteractGoal.class, P + "InteractGoal"),
            Map.entry(LeapAtTargetGoal.class, P + "LeapAtTargetGoal"),
            Map.entry(LlamaFollowCaravanGoal.class, P + "LlamaFollowCaravanGoal"),
            Map.entry(LookAtMobGoal.class, P + "LookAtPlayerGoal"),
            Map.entry(LookAtPlayerGoal.class, P + "LookAtPlayerGoal"),
            Map.entry(LookAtTradingPlayerGoal.class, P + "LookAtTradingPlayerGoal"),
            Map.entry(MeleeAttackGoal.class, P + "MeleeAttackGoal"),
            Map.entry(MoveThroughVillageGoal.class, P + "MoveThroughVillageGoal"),
            Map.entry(MoveToBlockGoal.class, P + "MoveToBlockGoal"),
            Map.entry(MoveTowardsRestrictionGoal.class, P + "MoveTowardsRestrictionGoal"),
            Map.entry(MoveTowardsTargetGoal.class, P + "MoveTowardsTargetGoal"),
            Map.entry(NearestAttackableTargetGoal.class, T + "NearestAttackableTargetGoal"),
            Map.entry(NonTameRandomTargetGoal.class, T + "NonTameRandomTargetGoal"),
            Map.entry(OcelotAttackGoal.class, P + "OcelotAttackGoal"),
            Map.entry(OpenDoorGoal.class, P + "OpenDoorGoal"),
            Map.entry(OwnerHurtByTargetGoal.class, T + "OwnerHurtByTargetGoal"),
            Map.entry(OwnerHurtTargetGoal.class, T + "OwnerHurtTargetGoal"),
            Map.entry(PanicGoal.class, P + "PanicGoal"),
            Map.entry(RandomLookAroundGoal.class, P + "RandomLookAroundGoal"),
            Map.entry(RandomStandGoal.class, P + "RandomStandGoal"),
            Map.entry(RandomStrollGoal.class, P + "RandomStrollGoal"),
            Map.entry(RandomSwimmingGoal.class, P + "RandomSwimmingGoal"),
            Map.entry(RangedAttackGoal.class, P + "RangedAttackGoal"),
            Map.entry(RangedBowAttackGoal.class, P + "RangedBowAttackGoal"),
            Map.entry(RangedCrossbowAttackGoal.class, P + "RangedCrossbowAttackGoal"),
            Map.entry(RemoveBlockGoal.class, P + "RemoveBlockGoal"),
            Map.entry(ResetUniversalAngerTargetGoal.class, T + "ResetUniversalAngerTargetGoal"),
            Map.entry(RestrictSunGoal.class, P + "RestrictSunGoal"),
            Map.entry(RunAroundLikeCrazyGoal.class, P + "RunAroundLikeCrazyGoal"),
            Map.entry(SitWhenOrderedToDoGoal.class, P + "SitWhenOrderedToGoal"),
            Map.entry(StrollThroughVillageGoal.class, P + "StrollThroughVillageGoal"),
            Map.entry(SwellGoal.class, P + "SwellGoal"),
            Map.entry(TargetGoal.class, T + "TargetGoal"),
            Map.entry(TemptGoal.class, P + "TemptGoal"),
            Map.entry(TradeWithPlayerGoal.class, P + "TradeWithPlayerGoal"),
            Map.entry(TryFindWaterGoal.class, P + "TryFindWaterGoal"),
            Map.entry(UseItemGoal.class, P + "UseItemGoal"),
            Map.entry(WaterAvoidingRandomFlyingGoal.class, P + "WaterAvoidingRandomFlyingGoal"),
            Map.entry(WaterAvoidingRandomStrollGoal.class, P + "WaterAvoidingRandomStrollGoal"),
            Map.entry(ZombieAttackGoal.class, P + "ZombieAttackGoal"));

    private static final ClassValue<String> BY_CLASS = new ClassValue<>() {
        @Override
        protected String computeValue(Class<?> c) {
            if (org.domi.furnace.server.entity.ai.WrappedGoal.class.isAssignableFrom(c)) return P + "WrappedGoal";
            for (Class<?> k = c; k != null; k = k.getSuperclass()) {
                String n = PORTED.get(k);
                if (n != null) return n;
            }
            return P + "Goal";   // 이식한 부모가 없는 몹 안쪽 목표 — 호환 계층이 바닐라 Goal 모양으로 보인다
        }
    };

    static String vanillaName(Class<?> serverGoalClass) { return BY_CLASS.get(serverGoalClass); }

    /** 이식한 목표 클래스 수(시험이 서버 ai.goal 패키지 전부를 덮는지 본다). */
    static int portedCount() { return PORTED.size(); }
}
