// 전리품 조건이 보는 피해 원인.
package org.domi.furnace.server.loot;

public interface LootDamageSource {

    /** 피해 종류 id(minecraft:player_attack …). */
    String type();

    /** 피해 종류 태그 포함 여부(minecraft:is_fire 등). */
    boolean is(String damageTypeTag);

    LootEntity entity();

    LootEntity directEntity();
}
