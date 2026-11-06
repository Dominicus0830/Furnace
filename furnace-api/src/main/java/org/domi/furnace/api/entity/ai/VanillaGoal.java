// 바닐라 목표를 다시 쓰기 위한 설명서(이름표 + 값). 서버가 그 몹에 맞는 실제 바닐라 목표로 만든다 — 동작은 바닐라 그 목표와 같다.
// 몹 종류가 그 목표를 쓸 수 없으면(예: 길찾기 못 하는 몹에 근접 공격, 동물이 아닌 몹에 번식) MobAi.addGoal 이 IllegalArgumentException.
// 엔티티·아이템 종류는 레지스트리 키 문자열("minecraft:player", "minecraft:wheat")이라 판이 바뀌어도 그대로 쓴다.
package org.domi.furnace.api.entity.ai;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class VanillaGoal {

    private final GoalKey key;
    private final Map<String, Object> params;

    private VanillaGoal(String name, Object... kv) {
        this.key = GoalKey.minecraft(name);
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], Objects.requireNonNull(kv[i + 1], (String) kv[i]));
        this.params = java.util.Collections.unmodifiableMap(m);
    }

    /** 이 목표의 이름표 — 몹에 처음부터 걸린 같은 종류 바닐라 목표와 같은 이름표다. */
    public GoalKey key() { return key; }

    /** 넣은 값(이름 → Double·Float·Integer·Boolean·String·List&lt;String&gt;). */
    public Map<String, Object> params() { return params; }

    // ------------------------------------------------------------------ 행동(GOALS)

    /** 물·용암에 뜨기(FloatGoal). 모든 몹. */
    public static VanillaGoal floatInWater() { return new VanillaGoal("float"); }

    /** 대상에게 다가가 때리기(MeleeAttackGoal). 길찾는 몹. */
    public static VanillaGoal meleeAttack(double speed, boolean followEvenIfNotSeen) {
        return new VanillaGoal("melee_attack", "speed", speed, "follow_even_if_not_seen", followEvenIfNotSeen);
    }

    /** 아무 데나 걷기(RandomStrollGoal, 기본 간격 120틱). 길찾는 몹. */
    public static VanillaGoal randomStroll(double speed) { return randomStroll(speed, 120); }

    public static VanillaGoal randomStroll(double speed, int interval) { return new VanillaGoal("random_stroll", "speed", speed, "interval", interval); }

    /** 물을 피해 걷기(WaterAvoidingRandomStrollGoal). 길찾는 몹. */
    public static VanillaGoal waterAvoidingRandomStroll(double speed) { return new VanillaGoal("water_avoiding_random_stroll", "speed", speed); }

    /** 헤엄쳐 돌아다니기(RandomSwimmingGoal). 길찾는 몹. */
    public static VanillaGoal randomSwimming(double speed, int interval) { return new VanillaGoal("random_swimming", "speed", speed, "interval", interval); }

    /** 물을 피해 날아다니기(WaterAvoidingRandomFlyingGoal). 길찾는 몹(나는 길찾기일 때 뜻이 있다). */
    public static VanillaGoal waterAvoidingRandomFlying(double speed) { return new VanillaGoal("water_avoiding_random_flying", "speed", speed); }

    /** 가까운 플레이어 쳐다보기(LookAtPlayerGoal, 확률 0.02). 모든 몹. */
    public static VanillaGoal lookAtPlayer(float distance) { return lookAtPlayer(distance, 0.02F); }

    public static VanillaGoal lookAtPlayer(float distance, float probability) {
        return new VanillaGoal("look_at_player", "distance", distance, "probability", probability);
    }

    /** 두리번거리기(RandomLookAroundGoal). 모든 몹. */
    public static VanillaGoal randomLookAround() { return new VanillaGoal("random_look_around"); }

    /** 맞거나 불붙으면 달아나기(PanicGoal). 길찾는 몹. */
    public static VanillaGoal panic(double speed) { return new VanillaGoal("panic", "speed", speed); }

    /** 이 아이템을 든 플레이어 따라가기(TemptGoal). 길찾는 몹. items 는 아이템 키. */
    public static VanillaGoal tempt(double speed, Collection<String> items, boolean canScare) {
        if (items.isEmpty()) throw new IllegalArgumentException("유혹 아이템이 없다");
        return new VanillaGoal("tempt", "speed", speed, "items", List.copyOf(items), "can_scare", canScare);
    }

    /** 그 종류 엔티티 피하기(AvoidEntityGoal). 길찾는 몹. entityType 은 엔티티 키("minecraft:player" 포함). */
    public static VanillaGoal avoidEntity(String entityType, float maxDistance, double walkSpeed, double sprintSpeed) {
        return new VanillaGoal("avoid_entity", "entity_type", entityType, "max_distance", maxDistance, "walk_speed", walkSpeed, "sprint_speed", sprintSpeed);
    }

    /** 대상에게 뛰어들기(LeapAtTargetGoal). 모든 몹. */
    public static VanillaGoal leapAtTarget(float yd) { return new VanillaGoal("leap_at_target", "yd", yd); }

    /** 대상 쪽으로 걷기(MoveTowardsTargetGoal). 길찾는 몹. */
    public static VanillaGoal moveTowardsTarget(double speed, float within) { return new VanillaGoal("move_towards_target", "speed", speed, "within", within); }

    /** 묶인 구역 안으로 돌아가기(MoveTowardsRestrictionGoal). 길찾는 몹. */
    public static VanillaGoal moveTowardsRestriction(double speed) { return new VanillaGoal("move_towards_restriction", "speed", speed); }

    /** 문 열기(OpenDoorGoal) — 땅 길찾기 몹. closeDoor 면 지나간 뒤 닫는다. */
    public static VanillaGoal openDoor(boolean closeDoor) { return new VanillaGoal("open_door", "close_door", closeDoor); }

    /** 해가 뜨면 그늘만 다니기(RestrictSunGoal). 길찾는 몹. */
    public static VanillaGoal restrictSun() { return new VanillaGoal("restrict_sun"); }

    /** 해를 피해 그늘로(FleeSunGoal). 길찾는 몹. */
    public static VanillaGoal fleeSun(double speed) { return new VanillaGoal("flee_sun", "speed", speed); }

    /** 숨이 차면 물 밖으로(BreathAirGoal). 길찾는 몹. */
    public static VanillaGoal breathAir() { return new VanillaGoal("breath_air"); }

    /** 짝짓기(BreedGoal). 동물. */
    public static VanillaGoal breed(double speed) { return new VanillaGoal("breed", "speed", speed); }

    /** 어미 따라가기(FollowParentGoal). 동물. */
    public static VanillaGoal followParent(double speed) { return new VanillaGoal("follow_parent", "speed", speed); }

    // ------------------------------------------------------------------ 대상(TARGETS)

    /** 가까운 그 종류를 공격 대상으로(NearestAttackableTargetGoal). 모든 몹. entityType 은 엔티티 키("minecraft:player" 포함). */
    public static VanillaGoal nearestAttackableTarget(String entityType, boolean mustSee) {
        return new VanillaGoal("nearest_attackable_target", "entity_type", entityType, "must_see", mustSee);
    }

    /** 때린 쪽을 공격 대상으로(HurtByTargetGoal). 길찾는 몹. */
    public static VanillaGoal hurtByTarget() { return new VanillaGoal("hurt_by_target"); }

    @Override
    public boolean equals(Object o) { return o instanceof VanillaGoal v && key.equals(v.key) && params.equals(v.params); }

    @Override
    public int hashCode() { return key.hashCode() * 31 + params.hashCode(); }

    @Override
    public String toString() { return key + params.toString(); }
}
