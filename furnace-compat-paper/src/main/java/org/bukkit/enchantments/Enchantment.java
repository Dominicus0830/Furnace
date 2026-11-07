// 이 파일은 tools/compat/gen-bukkit-registries.py 가 만든다 — 손으로 고치지 않는다.
// 마법 부여 레지스트리 1.21.1. 상수 이름은 키를 대문자로 한 것이다.
package org.bukkit.enchantments;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

/**
 * 마법 부여 종류. 키(minecraft:sharpness)로 구분한다. 최대 레벨·적용 가능한 아이템은 서버 데이터(데이터 팩이 바꿀 수 있음)에서
 * 오는 값이라 호환 계층이 지어내지 않는다 — getMaxLevel·getStartLevel·canEnchantItem 은 UnsupportedFeature.
 */
public final class Enchantment implements Keyed {

    private static final Map<String, Enchantment> BY_KEY = new LinkedHashMap<>();

    public static final Enchantment AQUA_AFFINITY = create("aqua_affinity");
    public static final Enchantment BANE_OF_ARTHROPODS = create("bane_of_arthropods");
    public static final Enchantment BINDING_CURSE = create("binding_curse");
    public static final Enchantment BLAST_PROTECTION = create("blast_protection");
    public static final Enchantment BREACH = create("breach");
    public static final Enchantment CHANNELING = create("channeling");
    public static final Enchantment DENSITY = create("density");
    public static final Enchantment DEPTH_STRIDER = create("depth_strider");
    public static final Enchantment EFFICIENCY = create("efficiency");
    public static final Enchantment FEATHER_FALLING = create("feather_falling");
    public static final Enchantment FIRE_ASPECT = create("fire_aspect");
    public static final Enchantment FIRE_PROTECTION = create("fire_protection");
    public static final Enchantment FLAME = create("flame");
    public static final Enchantment FORTUNE = create("fortune");
    public static final Enchantment FROST_WALKER = create("frost_walker");
    public static final Enchantment IMPALING = create("impaling");
    public static final Enchantment INFINITY = create("infinity");
    public static final Enchantment KNOCKBACK = create("knockback");
    public static final Enchantment LOOTING = create("looting");
    public static final Enchantment LOYALTY = create("loyalty");
    public static final Enchantment LUCK_OF_THE_SEA = create("luck_of_the_sea");
    public static final Enchantment LURE = create("lure");
    public static final Enchantment MENDING = create("mending");
    public static final Enchantment MULTISHOT = create("multishot");
    public static final Enchantment PIERCING = create("piercing");
    public static final Enchantment POWER = create("power");
    public static final Enchantment PROJECTILE_PROTECTION = create("projectile_protection");
    public static final Enchantment PROTECTION = create("protection");
    public static final Enchantment PUNCH = create("punch");
    public static final Enchantment QUICK_CHARGE = create("quick_charge");
    public static final Enchantment RESPIRATION = create("respiration");
    public static final Enchantment RIPTIDE = create("riptide");
    public static final Enchantment SHARPNESS = create("sharpness");
    public static final Enchantment SILK_TOUCH = create("silk_touch");
    public static final Enchantment SMITE = create("smite");
    public static final Enchantment SOUL_SPEED = create("soul_speed");
    public static final Enchantment SWEEPING_EDGE = create("sweeping_edge");
    public static final Enchantment SWIFT_SNEAK = create("swift_sneak");
    public static final Enchantment THORNS = create("thorns");
    public static final Enchantment UNBREAKING = create("unbreaking");
    public static final Enchantment VANISHING_CURSE = create("vanishing_curse");
    public static final Enchantment WIND_BURST = create("wind_burst");

    private final NamespacedKey key;

    private Enchantment(NamespacedKey key) { this.key = key; }

    private static Enchantment create(String path) {
        Enchantment e = new Enchantment(NamespacedKey.minecraft(path));
        BY_KEY.put(path, e);
        return e;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    /** 키로 찾는다(minecraft 네임스페이스만). 없으면 null. */
    public static Enchantment getByKey(NamespacedKey key) {
        if (key == null || !NamespacedKey.MINECRAFT.equals(key.getNamespace())) return null;
        return BY_KEY.get(key.getKey());
    }

    /** "minecraft:sharpness"·"sharpness"·"SHARPNESS" 로 찾는다. 없으면 null. */
    public static Enchantment fromKey(String name) {
        if (name == null) return null;
        String k = name.strip().toLowerCase(Locale.ROOT);
        if (k.startsWith("minecraft:")) k = k.substring("minecraft:".length());
        return BY_KEY.get(k);
    }

    /** 모든 마법 부여(키 순). */
    public static Iterable<Enchantment> all() { return Collections.unmodifiableCollection(BY_KEY.values()); }

    public int getMaxLevel() { throw new org.domi.furnace.compat.paper.UnsupportedFeature("Enchantment#getMaxLevel", "최대 레벨은 서버 데이터에서 오며 API 가 아직 알려 주지 않는다"); }

    public int getStartLevel() { throw new org.domi.furnace.compat.paper.UnsupportedFeature("Enchantment#getStartLevel", "시작 레벨은 서버 데이터에서 오며 API 가 아직 알려 주지 않는다"); }

    public boolean canEnchantItem(org.bukkit.inventory.ItemStack item) { throw new org.domi.furnace.compat.paper.UnsupportedFeature("Enchantment#canEnchantItem", "적용 가능한 아이템은 서버 데이터에서 오며 API 가 아직 알려 주지 않는다"); }

    @Override
    public boolean equals(Object o) { return o instanceof Enchantment e && e.key.equals(key); }

    @Override
    public int hashCode() { return key.hashCode(); }

    @Override
    public String toString() { return "Enchantment[" + key + "]"; }
}
