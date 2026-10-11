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

    default boolean onGround() { return true; }

    /** 장비 칸(mainhand·offhand·head·chest·legs·feet·body). 비었으면 빈 스택. */
    default LootStack equipment(String slot) { return LootStack.empty(); }

    default double[] position() { return null; }

    /** 종류별 속성(type_specific: slime size, raider is_captain, fishing_hook in_open_water, frog variant 등). 없으면 null. */
    default Object typeSpecific(String key) { return null; }

    default float luck() { return 0.0f; }
}
