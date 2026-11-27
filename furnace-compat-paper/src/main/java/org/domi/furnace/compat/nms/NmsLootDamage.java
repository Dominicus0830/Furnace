package org.domi.furnace.compat.nms;

import net.minecraft.world.entity.Entity;

/**
 * 전리품 문맥의 damage_source 값(바닐라 DamageSources.generic()·playerAttack(player) 에 해당). 호환 계층에 DamageSource 형이 아직 없어,
 * 전리품 다리({@code LootTable#getRandomItems} 등)가 읽는 모양(종류 id·원인·직접 가해자)만 담는다.
 */
public record NmsLootDamage(String type, Entity causing, Entity direct) {

    public static NmsLootDamage generic() { return new NmsLootDamage("minecraft:generic", null, null); }

    public static NmsLootDamage playerAttack(Entity player) { return new NmsLootDamage("minecraft:player_attack", player, player); }
}
