package org.bukkit.potion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.LivingEntity;

/** 상태 효과 값 하나(종류·지속 틱·증폭·환경·입자·아이콘). 불변. */
public class PotionEffect implements ConfigurationSerializable {

    /** 끝나지 않는 지속시간. */
    public static final int INFINITE_DURATION = -1;

    private static final String HIDDEN_EFFECT = "hidden_effect";
    private static final String AMPLIFIER = "amplifier";
    private static final String DURATION = "duration";
    private static final String TYPE = "effect";
    private static final String AMBIENT = "ambient";
    private static final String PARTICLES = "has-particles";
    private static final String ICON = "has-icon";

    private final int amplifier;
    private final int duration;
    private final PotionEffectType type;
    private final boolean ambient;
    private final boolean particles;
    private final boolean icon;
    private final PotionEffect hiddenEffect;

    /** Paper 내부용(숨은 효과 포함). */
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon, PotionEffect hiddenEffect) {
        if (type == null) throw new IllegalArgumentException("effect type cannot be null");
        this.type = type;
        this.duration = duration;
        this.amplifier = amplifier;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
        this.hiddenEffect = hiddenEffect;
    }

    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles, boolean icon) {
        this(type, duration, amplifier, ambient, particles, icon, null);
    }

    /** 아이콘은 입자를 따른다. */
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient, boolean particles) {
        this(type, duration, amplifier, ambient, particles, particles);
    }

    /** 입자를 보인다. */
    public PotionEffect(PotionEffectType type, int duration, int amplifier, boolean ambient) { this(type, duration, amplifier, ambient, true); }

    /** 환경 효과(ambient = true)로 만든다 — Bukkit 과 같다. */
    public PotionEffect(PotionEffectType type, int duration, int amplifier) { this(type, duration, amplifier, true); }

    /** serialize() 의 지도에서. */
    public PotionEffect(Map<String, Object> map) {
        this(effectType(map), intOf(map, DURATION), intOf(map, AMPLIFIER), boolOf(map, AMBIENT, false), boolOf(map, PARTICLES, true),
                boolOf(map, ICON, boolOf(map, PARTICLES, true)), (PotionEffect) map.get(HIDDEN_EFFECT));
    }

    private static PotionEffectType effectType(Map<?, ?> map) {
        PotionEffectType t;
        if (map.get(TYPE) instanceof String s) {
            NamespacedKey k = NamespacedKey.fromString(s);
            t = k == null ? null : PotionEffectType.getByKey(k);
        } else {
            t = PotionEffectType.getById(intOf(map, TYPE));
        }
        if (t == null) throw new NoSuchElementException(map + " does not contain " + TYPE);
        return t;
    }

    private static int intOf(Map<?, ?> map, Object key) {
        if (map.get(key) instanceof Integer i) return i;
        throw new NoSuchElementException(map + " does not contain " + key);
    }

    private static boolean boolOf(Map<?, ?> map, Object key, boolean def) { return map.get(key) instanceof Boolean b ? b : def; }

    public PotionEffect withType(PotionEffectType type) { return new PotionEffect(type, duration, amplifier, ambient, particles, icon); }

    public PotionEffect withDuration(int duration) { return new PotionEffect(type, duration, amplifier, ambient, particles, icon); }

    public PotionEffect withAmplifier(int amplifier) { return new PotionEffect(type, duration, amplifier, ambient, particles, icon); }

    public PotionEffect withAmbient(boolean ambient) { return new PotionEffect(type, duration, amplifier, ambient, particles, icon); }

    public PotionEffect withParticles(boolean particles) { return new PotionEffect(type, duration, amplifier, ambient, particles, icon); }

    public PotionEffect withIcon(boolean icon) { return new PotionEffect(type, duration, amplifier, ambient, particles, icon); }

    /** 더 센 효과에 가려진 약한 효과(끝나면 이것으로 돌아간다). 없으면 null. */
    public PotionEffect getHiddenPotionEffect() { return hiddenEffect; }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(TYPE, type.getKey().toString());
        m.put(DURATION, duration);
        m.put(AMPLIFIER, amplifier);
        m.put(AMBIENT, ambient);
        m.put(PARTICLES, particles);
        m.put(ICON, icon);
        if (hiddenEffect != null) m.put(HIDDEN_EFFECT, hiddenEffect);
        return Collections.unmodifiableMap(m);
    }

    /** entity.addPotionEffect(this). */
    public boolean apply(LivingEntity entity) { return entity.addPotionEffect(this); }

    public int getAmplifier() { return amplifier; }

    public int getDuration() { return duration; }

    public boolean isInfinite() { return duration == INFINITE_DURATION; }

    public boolean isShorterThan(PotionEffect other) { return !isInfinite() && (duration < other.duration || other.isInfinite()); }

    public PotionEffectType getType() { return type; }

    public boolean isAmbient() { return ambient; }

    public boolean hasParticles() { return particles; }

    public boolean hasIcon() { return icon; }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof PotionEffect o)) return false;
        return type.equals(o.type) && ambient == o.ambient && amplifier == o.amplifier && duration == o.duration && particles == o.particles
                && icon == o.icon && Objects.equals(hiddenEffect, o.hiddenEffect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amplifier, duration, ambient, particles, icon, hiddenEffect);
    }

    @Override
    public String toString() {
        return "PotionEffect{amplifier=" + amplifier + ", duration=" + duration + ", type=" + type + ", ambient=" + ambient + ", particles=" + particles
                + ", icon=" + icon + ", hiddenEffect=" + hiddenEffect + "}";
    }
}
