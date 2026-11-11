// 전리품 조건이 보는 엔티티(서버 엔티티 모델과 분리). 아직 없는 정보는 바닐라 기본 상태로 답한다.
package org.domi.furnace.server.loot;

public interface LootEntity {

    /** 엔티티 종류 id(minecraft:zombie …). */
    String type();

    default boolean isPlayer() { return type().equals("minecraft:player"); }

    default boolean isOnFire() { return false; }

    default boolean isCrouching() { return false; }

    default boolean isSprinting() { return false; }

    default boolean isSwimming() { return false; }

    default boolean isBaby() { return false; }

    /** EntityFlagsPredicate.is_flying: 살아 있는 엔티티는 겉날개 비행, 플레이어는 abilities.flying 도. */
    default boolean isFlying() { return false; }

    default boolean onGround() { return true; }

    /** 장비 칸(mainhand·offhand·head·chest·legs·feet·body). 비었으면 빈 스택. */
    default LootStack equipment(String slot) { return LootStack.empty(); }

    default double[] position() { return null; }

    /**
     * 종류별 속성(type_specific: slime size, raider is_captain, fishing_hook in_open_water, frog variant 등). 없으면 null.
     * 플레이어는 "stat:&lt;종류&gt;|&lt;값&gt;"(통계 현재 값, Integer, 없으면 0)와 "looking_at"(시선 끝의 엔티티 LootEntity —
     * 눈에서 시선 방향 100칸 안에서 처음 맞는 것이고 hasLineOfSight 까지 통과한 것만, 아니면 null)도 답한다.
     */
    default Object typeSpecific(String key) { return null; }

    default float luck() { return 0.0f; }

    /** 걸린 상태 효과(효과 id → 상태). 없으면 빈 맵. */
    default java.util.Map<String, Effect> effects() { return java.util.Map.of(); }

    /** 상태 효과 한 개의 값(EntityEffectPredicate.MobEffectInstancePredicate 가 보는 것). */
    record Effect(int amplifier, int duration, boolean ambient, boolean visible) {}

    /** 저장 NBT(NbtPredicate.getEntityTagToCompare — saveWithoutId + 플레이어는 SelectedItem). 모르면 null. */
    default org.domi.furnace.server.net.io.nbt.NbtCompound nbt() { return null; }

    default LootEntity vehicle() { return null; }

    default java.util.List<LootEntity> passengers() { return java.util.List.of(); }

    /** Mob.getTarget(). 몹이 아니거나 없으면 null. */
    default LootEntity targetedEntity() { return null; }

    /** 서 있는 칸 중심 위치(Vec3.atCenterOf(getOnPos)). */
    default double[] steppingOnPos() { return null; }

    /** 이동에 영향을 주는 아래 칸의 아래 중앙(Vec3.atBottomCenterOf(getBlockPosBelowThatAffectsMyMovement)). */
    default double[] movementAffectedByPos() { return null; }

    /** 속도(블록/틱). 모르면 null. */
    default double[] velocity() { return null; }

    default float fallDistance() { return 0.0f; }

    /** 팀 이름. 없으면 null. */
    default String team() { return null; }

    /** 슬롯 범위 이름(weapon.mainhand, container.0 …)의 칸 스택 목록. 모르는 이름이면 빈 목록. */
    default java.util.List<LootStack> slotItems(String range) { return java.util.List.of(); }

    default int tickCount() { return 0; }

    /** 플레이어의 게임 프로필(profile 컴포넌트 정규 값: name, id(int[4]), properties). 플레이어가 아니면 null. */
    default java.util.Map<String, Object> gameProfile() { return null; }
}
