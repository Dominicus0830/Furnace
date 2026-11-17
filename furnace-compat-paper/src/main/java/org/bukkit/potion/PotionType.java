// 이 파일은 tools/compat/gen-bukkit-values.py 가 만든다 — 손으로 고치지 않는다.
// potion 레지스트리 1.21.1 의 46 종(순서는 1.21.1 Bukkit 과 같다). 효과·지속·단계는 서버 effect.Potions(바닐라 표)에서 가져왔다.
// 1.20.5 이후 Bukkit 처럼 강화·연장 판(STRONG_·LONG_)은 따로 있는 상수다. 옛 이름(JUMP·SPEED·REGEN …)은 ApiRenames 가 바꿔 준다.
package org.bukkit.potion;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

public enum PotionType implements Keyed {
    WATER("water", false, false, List.of()),
    MUNDANE("mundane", false, false, List.of()),
    THICK("thick", false, false, List.of()),
    AWKWARD("awkward", false, false, List.of()),
    NIGHT_VISION("night_vision", false, true, List.of(new PotionEffect(PotionEffectType.NIGHT_VISION, 3600, 0, false, true, true))),
    LONG_NIGHT_VISION("long_night_vision", false, false, List.of(new PotionEffect(PotionEffectType.NIGHT_VISION, 9600, 0, false, true, true))),
    INVISIBILITY("invisibility", false, true, List.of(new PotionEffect(PotionEffectType.INVISIBILITY, 3600, 0, false, true, true))),
    LONG_INVISIBILITY("long_invisibility", false, false, List.of(new PotionEffect(PotionEffectType.INVISIBILITY, 9600, 0, false, true, true))),
    LEAPING("leaping", true, true, List.of(new PotionEffect(PotionEffectType.JUMP_BOOST, 3600, 0, false, true, true))),
    LONG_LEAPING("long_leaping", false, false, List.of(new PotionEffect(PotionEffectType.JUMP_BOOST, 9600, 0, false, true, true))),
    STRONG_LEAPING("strong_leaping", false, false, List.of(new PotionEffect(PotionEffectType.JUMP_BOOST, 1800, 1, false, true, true))),
    FIRE_RESISTANCE("fire_resistance", false, true, List.of(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 3600, 0, false, true, true))),
    LONG_FIRE_RESISTANCE("long_fire_resistance", false, false, List.of(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 9600, 0, false, true, true))),
    SWIFTNESS("swiftness", true, true, List.of(new PotionEffect(PotionEffectType.SPEED, 3600, 0, false, true, true))),
    LONG_SWIFTNESS("long_swiftness", false, false, List.of(new PotionEffect(PotionEffectType.SPEED, 9600, 0, false, true, true))),
    STRONG_SWIFTNESS("strong_swiftness", false, false, List.of(new PotionEffect(PotionEffectType.SPEED, 1800, 1, false, true, true))),
    SLOWNESS("slowness", true, true, List.of(new PotionEffect(PotionEffectType.SLOWNESS, 1800, 0, false, true, true))),
    LONG_SLOWNESS("long_slowness", false, false, List.of(new PotionEffect(PotionEffectType.SLOWNESS, 4800, 0, false, true, true))),
    STRONG_SLOWNESS("strong_slowness", false, false, List.of(new PotionEffect(PotionEffectType.SLOWNESS, 400, 3, false, true, true))),
    WATER_BREATHING("water_breathing", false, true, List.of(new PotionEffect(PotionEffectType.WATER_BREATHING, 3600, 0, false, true, true))),
    LONG_WATER_BREATHING("long_water_breathing", false, false, List.of(new PotionEffect(PotionEffectType.WATER_BREATHING, 9600, 0, false, true, true))),
    HEALING("healing", true, false, List.of(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 0, false, true, true))),
    STRONG_HEALING("strong_healing", false, false, List.of(new PotionEffect(PotionEffectType.INSTANT_HEALTH, 1, 1, false, true, true))),
    HARMING("harming", true, false, List.of(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0, false, true, true))),
    STRONG_HARMING("strong_harming", false, false, List.of(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 1, false, true, true))),
    POISON("poison", true, true, List.of(new PotionEffect(PotionEffectType.POISON, 900, 0, false, true, true))),
    LONG_POISON("long_poison", false, false, List.of(new PotionEffect(PotionEffectType.POISON, 1800, 0, false, true, true))),
    STRONG_POISON("strong_poison", false, false, List.of(new PotionEffect(PotionEffectType.POISON, 432, 1, false, true, true))),
    REGENERATION("regeneration", true, true, List.of(new PotionEffect(PotionEffectType.REGENERATION, 900, 0, false, true, true))),
    LONG_REGENERATION("long_regeneration", false, false, List.of(new PotionEffect(PotionEffectType.REGENERATION, 1800, 0, false, true, true))),
    STRONG_REGENERATION("strong_regeneration", false, false, List.of(new PotionEffect(PotionEffectType.REGENERATION, 450, 1, false, true, true))),
    STRENGTH("strength", true, true, List.of(new PotionEffect(PotionEffectType.STRENGTH, 3600, 0, false, true, true))),
    LONG_STRENGTH("long_strength", false, false, List.of(new PotionEffect(PotionEffectType.STRENGTH, 9600, 0, false, true, true))),
    STRONG_STRENGTH("strong_strength", false, false, List.of(new PotionEffect(PotionEffectType.STRENGTH, 1800, 1, false, true, true))),
    WEAKNESS("weakness", false, true, List.of(new PotionEffect(PotionEffectType.WEAKNESS, 1800, 0, false, true, true))),
    LONG_WEAKNESS("long_weakness", false, false, List.of(new PotionEffect(PotionEffectType.WEAKNESS, 4800, 0, false, true, true))),
    LUCK("luck", false, false, List.of(new PotionEffect(PotionEffectType.LUCK, 6000, 0, false, true, true))),
    TURTLE_MASTER("turtle_master", true, true, List.of(new PotionEffect(PotionEffectType.SLOWNESS, 400, 3, false, true, true), new PotionEffect(PotionEffectType.RESISTANCE, 400, 2, false, true, true))),
    LONG_TURTLE_MASTER("long_turtle_master", false, false, List.of(new PotionEffect(PotionEffectType.SLOWNESS, 800, 3, false, true, true), new PotionEffect(PotionEffectType.RESISTANCE, 800, 2, false, true, true))),
    STRONG_TURTLE_MASTER("strong_turtle_master", false, false, List.of(new PotionEffect(PotionEffectType.SLOWNESS, 400, 5, false, true, true), new PotionEffect(PotionEffectType.RESISTANCE, 400, 3, false, true, true))),
    SLOW_FALLING("slow_falling", false, true, List.of(new PotionEffect(PotionEffectType.SLOW_FALLING, 1800, 0, false, true, true))),
    LONG_SLOW_FALLING("long_slow_falling", false, false, List.of(new PotionEffect(PotionEffectType.SLOW_FALLING, 4800, 0, false, true, true))),
    WIND_CHARGED("wind_charged", false, false, List.of(new PotionEffect(PotionEffectType.WIND_CHARGED, 3600, 0, false, true, true))),
    WEAVING("weaving", false, false, List.of(new PotionEffect(PotionEffectType.WEAVING, 3600, 0, false, true, true))),
    OOZING("oozing", false, false, List.of(new PotionEffect(PotionEffectType.OOZING, 3600, 0, false, true, true))),
    INFESTED("infested", false, false, List.of(new PotionEffect(PotionEffectType.INFESTED, 3600, 0, false, true, true)));

    private static final Map<NamespacedKey, PotionType> BY_KEY = new HashMap<>();

    static {
        for (PotionType t : values()) BY_KEY.put(t.key, t);
    }

    private final NamespacedKey key;
    private final boolean upgradeable;
    private final boolean extendable;
    private final List<PotionEffect> effects;

    PotionType(String key, boolean upgradeable, boolean extendable, List<PotionEffect> effects) {
        this.key = NamespacedKey.minecraft(key);
        this.upgradeable = upgradeable;
        this.extendable = extendable;
        this.effects = effects;
    }

    /** @deprecated 첫 효과의 종류(효과가 없으면 null). */
    @Deprecated
    public PotionEffectType getEffectType() { return effects.isEmpty() ? null : effects.get(0).getType(); }

    /** 이 물약이 거는 효과(바꿀 수 없는 목록). */
    public List<PotionEffect> getPotionEffects() { return effects; }

    /** @deprecated 첫 효과가 즉시 효과인가. */
    @Deprecated
    public boolean isInstant() { return !effects.isEmpty() && effects.get(0).getType().isInstant(); }

    /** 강화 판(STRONG_ 이름)이 있는가. */
    public boolean isUpgradeable() { return upgradeable; }

    /** 연장 판(LONG_ 이름)이 있는가. */
    public boolean isExtendable() { return extendable; }

    /** 강화 판이 있으면 2, 아니면 1. */
    public int getMaxLevel() { return upgradeable ? 2 : 1; }

    @Override
    public NamespacedKey getKey() { return key; }

    /** @deprecated 그 효과를 첫 효과로 거는 첫 물약 종류(null 이면 WATER, 없으면 null). */
    @Deprecated
    public static PotionType getByEffect(PotionEffectType effectType) {
        if (effectType == null) return WATER;
        for (PotionType t : values()) if (effectType.equals(t.getEffectType())) return t;
        return null;
    }

    /** 호환 모듈 전용 — 키로 찾기(없으면 null). */
    public static PotionType byKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key); }
}
