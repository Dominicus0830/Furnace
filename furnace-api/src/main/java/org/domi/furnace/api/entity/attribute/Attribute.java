// 엔티티 속성 종류(바닐라 1.21.1 minecraft:attribute 레지스트리 전부) — 키·기본값·최소·최대. 불변 값이라 어느 스레드에서나 쓴다.
// 키는 1.21.1 이름(minecraft:generic.movement_speed)이다. 1.21.2 이후의 접두사 없는 이름(minecraft:movement_speed)과 옛 Bukkit 이름
// (generic.movementSpeed, horse.jumpStrength)도 find/of 가 받아 같은 속성으로 돌려준다 — 코드는 어느 쪽 이름을 써도 된다.
package org.domi.furnace.api.entity.attribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class Attribute {

    private static final List<Attribute> ALL = new ArrayList<>();
    private static final Map<String, Attribute> BY_KEY = new HashMap<>();
    /** generic./player./zombie. 접두사를 뗀 이름 → 속성(1.21.2 이후 이름). */
    private static final Map<String, Attribute> BY_SHORT = new HashMap<>();

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
    public static final Attribute JUMP_STRENGTH = of("generic.jump_strength", 0.42F, 0, 32);   // 바닐라 기본값은 float 리터럴 0.42F 를 double 로 넓힌 값(0.41999998688697815)
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

    private final String key;
    private final double defaultValue, minValue, maxValue;

    private Attribute(String key, double defaultValue, double minValue, double maxValue) {
        this.key = key;
        this.defaultValue = defaultValue;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    private static Attribute of(String path, double def, double min, double max) {
        Attribute a = new Attribute("minecraft:" + path, def, min, max);
        ALL.add(a);
        BY_KEY.put(a.key, a);
        BY_SHORT.put(path.substring(path.indexOf('.') + 1), a);
        return a;
    }

    /** 레지스트리 순서의 전체 목록(바뀌지 않는 목록). */
    public static List<Attribute> values() { return Collections.unmodifiableList(ALL); }

    /**
     * 키로 찾는다. 받는 이름: 1.21.1 키(minecraft:generic.movement_speed), 접두사 없는 이름(movement_speed, generic.movement_speed),
     * 1.21.2 이후 이름(minecraft:movement_speed), Paper 가 옛 Bukkit 이름을 바꿔 주던 것(generic.movementSpeed, "Movement Speed",
     * horse.jump_strength). 대소문자는 구별한다(바닐라 키는 소문자). 모르면 empty.
     */
    public static Optional<Attribute> find(String name) {
        if (name == null) return Optional.empty();
        String n = LEGACY.getOrDefault(name, name);
        if (n.startsWith("minecraft:")) n = n.substring("minecraft:".length());
        else if (n.indexOf(':') >= 0) return Optional.empty();
        Attribute a = BY_KEY.get("minecraft:" + n);
        if (a == null) a = BY_SHORT.get(n);
        return Optional.ofNullable(a);
    }

    /** {@link #find} 인데 모르는 이름이면 IllegalArgumentException. */
    public static Attribute of(String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("모르는 속성: " + name));
    }

    // Paper CraftAttributeMap.legacyNMS 와 같은 목록(옛 Bukkit·NBT 이름 → 1.21.1 이름)
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry("generic.maxHealth", "generic.max_health"), Map.entry("Max Health", "generic.max_health"),
            Map.entry("zombie.spawnReinforcements", "zombie.spawn_reinforcements"), Map.entry("Spawn Reinforcements Chance", "zombie.spawn_reinforcements"),
            Map.entry("horse.jumpStrength", "generic.jump_strength"), Map.entry("horse.jump_strength", "generic.jump_strength"), Map.entry("Jump Strength", "generic.jump_strength"),
            Map.entry("generic.followRange", "generic.follow_range"), Map.entry("Follow Range", "generic.follow_range"),
            Map.entry("generic.knockbackResistance", "generic.knockback_resistance"), Map.entry("Knockback Resistance", "generic.knockback_resistance"),
            Map.entry("generic.movementSpeed", "generic.movement_speed"), Map.entry("Movement Speed", "generic.movement_speed"),
            Map.entry("generic.flyingSpeed", "generic.flying_speed"), Map.entry("Flying Speed", "generic.flying_speed"),
            Map.entry("generic.attackDamage", "generic.attack_damage"), Map.entry("generic.attackKnockback", "generic.attack_knockback"),
            Map.entry("generic.attackSpeed", "generic.attack_speed"), Map.entry("generic.armorToughness", "generic.armor_toughness"));

    /** 1.21.1 키(minecraft:generic.movement_speed). */
    public String key() { return key; }

    /** 이 속성의 기본값(엔티티 종류가 다른 시작 값을 정할 수 있다 — 플레이어 이동 속도 0.1. 그 엔티티의 현재 기본값은 {@code AttributeHandle#baseValue}). */
    public double defaultValue() { return defaultValue; }

    /** 값이 잘리는 최소(바닐라 RangedAttribute). */
    public double minValue() { return minValue; }

    /** 값이 잘리는 최대. */
    public double maxValue() { return maxValue; }

    /** 바닐라 RangedAttribute.sanitizeValue: NaN 은 최소로, 그 밖은 [최소, 최대] 로 자른다. */
    public double sanitize(double v) { return Double.isNaN(v) ? minValue : Math.clamp(v, minValue, maxValue); }

    @Override
    public String toString() { return key; }

    @Override
    public boolean equals(Object o) { return o instanceof Attribute a && a.key.equals(key); }

    @Override
    public int hashCode() { return Objects.hash(key); }

}
