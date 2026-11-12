// 이 파일은 tools/compat/gen-bukkit-values.py 가 만든다 — 손으로 고치지 않는다.
// attribute 레지스트리 1.21.1 의 31 종(1.21.1 Bukkit 이름 GENERIC_·PLAYER_ 접두 그대로). 순서는 Bukkit 이 쓰는 순서(가정).
package org.bukkit.attribute;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum Attribute implements Keyed {
    GENERIC_MAX_HEALTH("generic.max_health"),
    GENERIC_FOLLOW_RANGE("generic.follow_range"),
    GENERIC_KNOCKBACK_RESISTANCE("generic.knockback_resistance"),
    GENERIC_MOVEMENT_SPEED("generic.movement_speed"),
    GENERIC_FLYING_SPEED("generic.flying_speed"),
    GENERIC_ATTACK_DAMAGE("generic.attack_damage"),
    GENERIC_ATTACK_KNOCKBACK("generic.attack_knockback"),
    GENERIC_ATTACK_SPEED("generic.attack_speed"),
    GENERIC_ARMOR("generic.armor"),
    GENERIC_ARMOR_TOUGHNESS("generic.armor_toughness"),
    GENERIC_FALL_DAMAGE_MULTIPLIER("generic.fall_damage_multiplier"),
    GENERIC_LUCK("generic.luck"),
    GENERIC_MAX_ABSORPTION("generic.max_absorption"),
    GENERIC_SAFE_FALL_DISTANCE("generic.safe_fall_distance"),
    GENERIC_SCALE("generic.scale"),
    GENERIC_STEP_HEIGHT("generic.step_height"),
    GENERIC_GRAVITY("generic.gravity"),
    GENERIC_JUMP_STRENGTH("generic.jump_strength"),
    GENERIC_BURNING_TIME("generic.burning_time"),
    GENERIC_EXPLOSION_KNOCKBACK_RESISTANCE("generic.explosion_knockback_resistance"),
    GENERIC_MOVEMENT_EFFICIENCY("generic.movement_efficiency"),
    GENERIC_OXYGEN_BONUS("generic.oxygen_bonus"),
    GENERIC_WATER_MOVEMENT_EFFICIENCY("generic.water_movement_efficiency"),
    PLAYER_BLOCK_INTERACTION_RANGE("player.block_interaction_range"),
    PLAYER_ENTITY_INTERACTION_RANGE("player.entity_interaction_range"),
    PLAYER_BLOCK_BREAK_SPEED("player.block_break_speed"),
    PLAYER_MINING_EFFICIENCY("player.mining_efficiency"),
    PLAYER_SNEAKING_SPEED("player.sneaking_speed"),
    PLAYER_SUBMERGED_MINING_SPEED("player.submerged_mining_speed"),
    PLAYER_SWEEPING_DAMAGE_RATIO("player.sweeping_damage_ratio"),
    ZOMBIE_SPAWN_REINFORCEMENTS("zombie.spawn_reinforcements");

    private static final Map<NamespacedKey, Attribute> BY_KEY = new HashMap<>();

    static {
        for (Attribute a : values()) BY_KEY.put(a.key, a);
    }

    private final NamespacedKey key;

    Attribute(String key) { this.key = NamespacedKey.minecraft(key); }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 번역 키(attribute.name.generic.max_health). */
    public String getTranslationKey() { return "attribute.name." + key.getKey(); }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static Attribute byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
