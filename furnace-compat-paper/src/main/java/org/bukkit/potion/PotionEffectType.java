// 상태 효과 종류 — minecraft:mob_effect 레지스트리 1.21.1 의 39종(레지스트리 순서). 상수 이름은 키를 대문자로 한 것이다.
// 분류·즉시 여부는 바닐라 정의 값이다(서버 effect.MobEffect 와 같은지 시험이 본다). 색·속성 수정치는 서버 데이터라 두지 않는다.
package org.bukkit.potion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;

/** 상태 효과 종류. 키(minecraft:speed)로 구분한다. Paper 처럼 추상 클래스이고 같은 키는 같은 객체다. */
public abstract class PotionEffectType implements Keyed {

    private static final Map<String, PotionEffectType> BY_KEY = new LinkedHashMap<>();
    private static final Map<Integer, PotionEffectType> BY_ID = new LinkedHashMap<>();

    public static final PotionEffectType SPEED = create(1, "speed", PotionEffectTypeCategory.BENEFICIAL, false, "SPEED");
    public static final PotionEffectType SLOWNESS = create(2, "slowness", PotionEffectTypeCategory.HARMFUL, false, "SLOW");
    public static final PotionEffectType HASTE = create(3, "haste", PotionEffectTypeCategory.BENEFICIAL, false, "FAST_DIGGING");
    public static final PotionEffectType MINING_FATIGUE = create(4, "mining_fatigue", PotionEffectTypeCategory.HARMFUL, false, "SLOW_DIGGING");
    public static final PotionEffectType STRENGTH = create(5, "strength", PotionEffectTypeCategory.BENEFICIAL, false, "INCREASE_DAMAGE");
    public static final PotionEffectType INSTANT_HEALTH = create(6, "instant_health", PotionEffectTypeCategory.BENEFICIAL, true, "HEAL");
    public static final PotionEffectType INSTANT_DAMAGE = create(7, "instant_damage", PotionEffectTypeCategory.HARMFUL, true, "HARM");
    public static final PotionEffectType JUMP_BOOST = create(8, "jump_boost", PotionEffectTypeCategory.BENEFICIAL, false, "JUMP");
    public static final PotionEffectType NAUSEA = create(9, "nausea", PotionEffectTypeCategory.HARMFUL, false, "CONFUSION");
    public static final PotionEffectType REGENERATION = create(10, "regeneration", PotionEffectTypeCategory.BENEFICIAL, false, "REGENERATION");
    public static final PotionEffectType RESISTANCE = create(11, "resistance", PotionEffectTypeCategory.BENEFICIAL, false, "DAMAGE_RESISTANCE");
    public static final PotionEffectType FIRE_RESISTANCE = create(12, "fire_resistance", PotionEffectTypeCategory.BENEFICIAL, false, "FIRE_RESISTANCE");
    public static final PotionEffectType WATER_BREATHING = create(13, "water_breathing", PotionEffectTypeCategory.BENEFICIAL, false, "WATER_BREATHING");
    public static final PotionEffectType INVISIBILITY = create(14, "invisibility", PotionEffectTypeCategory.BENEFICIAL, false, "INVISIBILITY");
    public static final PotionEffectType BLINDNESS = create(15, "blindness", PotionEffectTypeCategory.HARMFUL, false, "BLINDNESS");
    public static final PotionEffectType NIGHT_VISION = create(16, "night_vision", PotionEffectTypeCategory.BENEFICIAL, false, "NIGHT_VISION");
    public static final PotionEffectType HUNGER = create(17, "hunger", PotionEffectTypeCategory.HARMFUL, false, "HUNGER");
    public static final PotionEffectType WEAKNESS = create(18, "weakness", PotionEffectTypeCategory.HARMFUL, false, "WEAKNESS");
    public static final PotionEffectType POISON = create(19, "poison", PotionEffectTypeCategory.HARMFUL, false, "POISON");
    public static final PotionEffectType WITHER = create(20, "wither", PotionEffectTypeCategory.HARMFUL, false, "WITHER");
    public static final PotionEffectType HEALTH_BOOST = create(21, "health_boost", PotionEffectTypeCategory.BENEFICIAL, false, "HEALTH_BOOST");
    public static final PotionEffectType ABSORPTION = create(22, "absorption", PotionEffectTypeCategory.BENEFICIAL, false, "ABSORPTION");
    public static final PotionEffectType SATURATION = create(23, "saturation", PotionEffectTypeCategory.BENEFICIAL, true, "SATURATION");
    public static final PotionEffectType GLOWING = create(24, "glowing", PotionEffectTypeCategory.NEUTRAL, false, "GLOWING");
    public static final PotionEffectType LEVITATION = create(25, "levitation", PotionEffectTypeCategory.HARMFUL, false, "LEVITATION");
    public static final PotionEffectType LUCK = create(26, "luck", PotionEffectTypeCategory.BENEFICIAL, false, "LUCK");
    public static final PotionEffectType UNLUCK = create(27, "unluck", PotionEffectTypeCategory.HARMFUL, false, "UNLUCK");
    public static final PotionEffectType SLOW_FALLING = create(28, "slow_falling", PotionEffectTypeCategory.BENEFICIAL, false, "SLOW_FALLING");
    public static final PotionEffectType CONDUIT_POWER = create(29, "conduit_power", PotionEffectTypeCategory.BENEFICIAL, false, "CONDUIT_POWER");
    public static final PotionEffectType DOLPHINS_GRACE = create(30, "dolphins_grace", PotionEffectTypeCategory.BENEFICIAL, false, "DOLPHINS_GRACE");
    public static final PotionEffectType BAD_OMEN = create(31, "bad_omen", PotionEffectTypeCategory.NEUTRAL, false, "BAD_OMEN");
    public static final PotionEffectType HERO_OF_THE_VILLAGE = create(32, "hero_of_the_village", PotionEffectTypeCategory.BENEFICIAL, false, "HERO_OF_THE_VILLAGE");
    public static final PotionEffectType DARKNESS = create(33, "darkness", PotionEffectTypeCategory.HARMFUL, false, "DARKNESS");
    public static final PotionEffectType TRIAL_OMEN = create(34, "trial_omen", PotionEffectTypeCategory.NEUTRAL, false, null);
    public static final PotionEffectType RAID_OMEN = create(35, "raid_omen", PotionEffectTypeCategory.NEUTRAL, false, null);
    public static final PotionEffectType WIND_CHARGED = create(36, "wind_charged", PotionEffectTypeCategory.HARMFUL, false, null);
    public static final PotionEffectType WEAVING = create(37, "weaving", PotionEffectTypeCategory.HARMFUL, false, null);
    public static final PotionEffectType OOZING = create(38, "oozing", PotionEffectTypeCategory.HARMFUL, false, null);
    public static final PotionEffectType INFESTED = create(39, "infested", PotionEffectTypeCategory.HARMFUL, false, null);

    private static PotionEffectType create(int id, String key, PotionEffectTypeCategory category, boolean instant, String legacyName) {
        PotionEffectType t = new Vanilla(id, NamespacedKey.minecraft(key), category, instant, legacyName);
        BY_KEY.put(t.getKey().toString(), t);
        BY_ID.put(id, t);
        return t;
    }

    protected PotionEffectType() {}

    /** 이 종류의 효과(즉시 효과는 지속시간 1). */
    public abstract PotionEffect createEffect(int duration, int amplifier);

    /** 즉시 효과인가(즉시 회복·즉시 피해·포화). */
    public abstract boolean isInstant();

    public abstract PotionEffectTypeCategory getCategory();

    /** @deprecated 늘 1.0(Bukkit 과 같다). */
    @Deprecated
    public abstract double getDurationModifier();

    /** @deprecated 옛 번호(레지스트리 번호 + 1). */
    @Deprecated
    public abstract int getId();

    /** @deprecated 옛 이름(SLOW, INCREASE_DAMAGE …). 옛 이름이 없는 새 효과는 키 문자열. */
    @Deprecated
    public abstract String getName();

    /** 번역 키(effect.minecraft.speed). */
    public String translationKey() { return "effect." + getKey().getNamespace() + "." + getKey().getKey(); }

    /** @deprecated 키로 찾는다. 없으면 null. */
    @Deprecated
    public static PotionEffectType getByKey(NamespacedKey key) { return key == null ? null : BY_KEY.get(key.toString()); }

    /** @deprecated 옛 번호로 찾는다. 없으면 null. */
    @Deprecated
    public static PotionEffectType getById(int id) { return BY_ID.get(id); }

    /**
     * @deprecated 키 문자열(대소문자 무시, 이름공간 생략 가능)로 찾는다. 옛 이름(SLOW·INCREASE_DAMAGE)은 Paper 처럼 플러그인 클래스를 올릴 때
     * 바꾼 다리(compat.nms.ApiRenames)가 먼저 새 이름으로 바꿔 넘긴다 — 이 메서드 자체는 옛 이름을 모른다(Bukkit 과 같다).
     */
    @Deprecated
    public static PotionEffectType getByName(String name) {
        if (name == null) throw new IllegalArgumentException("name cannot be null");
        NamespacedKey k = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
        return k == null ? null : BY_KEY.get(k.toString());
    }

    /** @deprecated 모든 종류(레지스트리 순서). */
    @Deprecated
    public static PotionEffectType[] values() { return BY_KEY.values().toArray(new PotionEffectType[0]); }

    /** 호환 모듈 전용: 키 문자열(minecraft:speed)로. 없으면 null. */
    public static PotionEffectType fromKey(String key) {
        if (key == null) return null;
        return BY_KEY.get(key.indexOf(':') < 0 ? "minecraft:" + key : key);
    }

    /** 호환 모듈 전용: 모든 종류(바꿀 수 없음). */
    public static java.util.Collection<PotionEffectType> all() { return Collections.unmodifiableCollection(BY_KEY.values()); }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof PotionEffectType t && t.getKey().equals(getKey()); }

    @Override
    public int hashCode() { return getKey().hashCode(); }

    @Override
    public String toString() { return "CraftPotionEffectType[" + getKey() + "]"; }

    /** 바닐라 효과. */
    private static final class Vanilla extends PotionEffectType {

        private final int id;
        private final NamespacedKey key;
        private final PotionEffectTypeCategory category;
        private final boolean instant;
        private final String legacyName;

        Vanilla(int id, NamespacedKey key, PotionEffectTypeCategory category, boolean instant, String legacyName) {
            this.id = id;
            this.key = key;
            this.category = category;
            this.instant = instant;
            this.legacyName = legacyName;
        }

        @Override
        public NamespacedKey getKey() { return key; }

        @Override
        public PotionEffect createEffect(int duration, int amplifier) { return new PotionEffect(this, instant ? 1 : duration, amplifier); }

        @Override
        public boolean isInstant() { return instant; }

        @Override
        public PotionEffectTypeCategory getCategory() { return category; }

        @Override
        public double getDurationModifier() { return 1.0D; }

        @Override
        public int getId() { return id; }

        @Override
        public String getName() { return legacyName != null ? legacyName : key.toString(); }
    }
}
