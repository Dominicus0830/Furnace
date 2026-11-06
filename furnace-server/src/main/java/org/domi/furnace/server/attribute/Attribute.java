// 바닐라 Attributes 레지스트리(minecraft:attribute 순서 그대로): 기본값·최소·최대.
package org.domi.furnace.server.attribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Attribute {
    private static final List<Attribute> ALL = new ArrayList<>();
    private static final Map<String, Attribute> BY_NAME = new HashMap<>();

    public static final Attribute ARMOR = of("generic.armor", 0, 0, 30);
    public static final Attribute ARMOR_TOUGHNESS = of("generic.armor_toughness", 0, 0, 20);
    public static final Attribute ATTACK_DAMAGE = of("generic.attack_damage", 2, 0, 2048);
    public static final Attribute ATTACK_KNOCKBACK = of("generic.attack_knockback", 0, 0, 5);
    public static final Attribute ATTACK_SPEED = of("generic.attack_speed", 4, 0, 1024);
    public static final Attribute BLOCK_BREAK_SPEED = of("player.block_break_speed", 1, 0, 1024);
    public static final Attribute BLOCK_INTERACTION_RANGE = of("player.block_interaction_range", 4.5, 0, 64);
    public static final Attribute BURNING_TIME = of("generic.burning_time", 1, 0, 1024);
    public static final Attribute EXPLOSION_KNOCKBACK_RESISTANCE = of("generic.explosion_knockback_resistance", 0, 0, 1);
    public static final Attribute ENTITY_INTERACTION_RANGE = of("player.entity_interaction_range", 3, 0, 64);
    public static final Attribute FALL_DAMAGE_MULTIPLIER = of("generic.fall_damage_multiplier", 1, 0, 100);
    public static final Attribute FLYING_SPEED = of("generic.flying_speed", 0.4, 0, 1024);
    public static final Attribute FOLLOW_RANGE = of("generic.follow_range", 32, 0, 2048);
    public static final Attribute GRAVITY = of("generic.gravity", 0.08, -1, 1);
    public static final Attribute JUMP_STRENGTH = of("generic.jump_strength", 0.42, 0, 32);
    public static final Attribute KNOCKBACK_RESISTANCE = of("generic.knockback_resistance", 0, 0, 1);
    public static final Attribute LUCK = of("generic.luck", 0, -1024, 1024);
    public static final Attribute MAX_ABSORPTION = of("generic.max_absorption", 0, 0, 2048);
    public static final Attribute MAX_HEALTH = of("generic.max_health", 20, 1, 1024);
    public static final Attribute MINING_EFFICIENCY = of("player.mining_efficiency", 0, 0, 1024);
    public static final Attribute MOVEMENT_EFFICIENCY = of("generic.movement_efficiency", 0, 0, 1);
    public static final Attribute MOVEMENT_SPEED = of("generic.movement_speed", 0.7, 0, 1024);
    public static final Attribute OXYGEN_BONUS = of("generic.oxygen_bonus", 0, 0, 1024);
    public static final Attribute SAFE_FALL_DISTANCE = of("generic.safe_fall_distance", 3, -1024, 1024);
    public static final Attribute SCALE = of("generic.scale", 1, 0.0625, 16);
    public static final Attribute SNEAKING_SPEED = of("player.sneaking_speed", 0.3, 0, 1);
    public static final Attribute SPAWN_REINFORCEMENTS = of("zombie.spawn_reinforcements", 0, 0, 1);
    public static final Attribute STEP_HEIGHT = of("generic.step_height", 0.6, 0, 10);
    public static final Attribute SUBMERGED_MINING_SPEED = of("player.submerged_mining_speed", 0.2, 0, 20);
    public static final Attribute SWEEPING_DAMAGE_RATIO = of("player.sweeping_damage_ratio", 0, 0, 1);
    public static final Attribute WATER_MOVEMENT_EFFICIENCY = of("generic.water_movement_efficiency", 0, 0, 1);

    private final int id;
    private final String name;
    private final double base, min, max;

    private Attribute(int id, String name, double base, double min, double max) {
        this.id = id;
        this.name = name;
        this.base = base;
        this.min = min;
        this.max = max;
    }

    private static Attribute of(String name, double base, double min, double max) {
        Attribute a = new Attribute(ALL.size(), "minecraft:" + name, base, min, max);
        ALL.add(a);
        BY_NAME.put(a.name, a);
        return a;
    }

    public static Attribute byName(String name) {
        if (name != null && name.indexOf(':') < 0) name = "minecraft:" + name;
        return BY_NAME.get(name);
    }

    public static Attribute byId(int id) { return ALL.get(id); }

    public int id() { return id; }

    public String name() { return name; }

    /** 클라이언트에 보내는 속성인가(바닐라 setSyncable). 공격력·밀치기·따라가기·밀려남 저항·폭발 저항·좀비 지원군은 서버 전용. */
    public boolean syncable() {
        return this != ATTACK_DAMAGE && this != ATTACK_KNOCKBACK && this != FOLLOW_RANGE && this != KNOCKBACK_RESISTANCE
                && this != EXPLOSION_KNOCKBACK_RESISTANCE && this != SPAWN_REINFORCEMENTS;
    }

    public double defaultValue() { return base; }

    public double sanitize(double v) { return Double.isNaN(v) ? min : Math.clamp(v, min, max); }
}
