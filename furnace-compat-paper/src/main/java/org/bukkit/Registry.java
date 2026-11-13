package org.bukkit;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/**
 * 키로 찾는 값 목록(1.21.1 Bukkit·Paper 의 Registry). 이 호환 계층에 값 타입이 있는 레지스트리만 상수로 둔다 — 없는 것(구조물·트림·변종 등)은
 * 상수 자체가 없어 그 필드를 읽는 플러그인은 NoSuchFieldError 로 그 자리에서 멈춘다(엉뚱한 빈 목록을 주지 않는다).
 * Paper 의 adventure Key·TypedKey 판 getOrThrow 는 Adventure 를 쓸 때만 받아 오므로 없다.
 */
public interface Registry<T extends Keyed> extends Iterable<T> {

    /** 그림 변종. */
    Registry<Art> ART = new SimpleRegistry<>(Art.class);
    /** 엔티티 속성. */
    Registry<Attribute> ATTRIBUTE = new SimpleRegistry<>(Attribute.class);
    /** 바닐라 바이옴(CUSTOM 은 없다). */
    Registry<Biome> BIOME = new SimpleRegistry<>(Biome.class, b -> b != Biome.CUSTOM);
    /** 마법부여. */
    Registry<Enchantment> ENCHANTMENT = org.domi.furnace.compat.paper.CompatRegistries.fixed(Enchantment.class, Enchantment.all());
    /** 엔티티 종류(UNKNOWN 은 없다). */
    Registry<EntityType> ENTITY_TYPE = new SimpleRegistry<>(EntityType.class, t -> t != EntityType.UNKNOWN);
    /** 재료(블록·아이템). */
    Registry<Material> MATERIAL = new SimpleRegistry<>(Material.class);
    /** 상태 효과 종류. */
    Registry<PotionEffectType> EFFECT = org.domi.furnace.compat.paper.CompatRegistries.fixed(PotionEffectType.class, PotionEffectType.all());
    /** 입자 종류. */
    Registry<Particle> PARTICLE_TYPE = new SimpleRegistry<>(Particle.class);
    /** 물약 종류. */
    Registry<PotionType> POTION = new SimpleRegistry<>(PotionType.class);
    /** 소리. */
    Registry<Sound> SOUNDS = new SimpleRegistry<>(Sound.class);

    /** 키의 값(없으면 null). */
    T get(NamespacedKey key);

    /** 키의 값 — 없으면 IllegalArgumentException(Bukkit 과 같다). */
    default T getOrThrow(NamespacedKey key) {
        T value = get(key);
        if (value == null) throw new IllegalArgumentException("No registry entry found for key " + key + ".");
        return value;
    }

    /** 값의 키(Paper). 레지스트리 밖 값이면 null. */
    default NamespacedKey getKey(T value) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        return value.getKey();
    }

    /** 값의 키(Paper) — 없으면 NoSuchElementException. */
    default NamespacedKey getKeyOrThrow(T value) {
        if (value == null) throw new IllegalArgumentException("value cannot be null");
        NamespacedKey key = getKey(value);
        if (key == null) throw new NoSuchElementException(value + " has no key in " + this);
        return key;
    }

    Stream<T> stream();

    /** 사람이 쓴 이름("Diamond Sword"·"minecraft:stone")으로 찾기: 소문자로, 공백 묶음은 '_' 로 바꿔 키로 읽는다. 없으면 null. */
    default T match(String input) {
        if (input == null) throw new IllegalArgumentException("input must not be null");
        NamespacedKey key = NamespacedKey.fromString(input.toLowerCase(Locale.ROOT).replaceAll("\\s+", "_"));
        return key == null ? null : get(key);
    }

    /** 열거형 값의 레지스트리(Bukkit 과 같은 이름 — 플러그인이 직접 만들기도 한다). */
    class SimpleRegistry<T extends Enum<T> & Keyed> implements Registry<T> {

        private final Class<T> type;
        private final Map<NamespacedKey, T> map;

        public SimpleRegistry(Class<T> type) { this(type, t -> true); }

        public SimpleRegistry(Class<T> type, Predicate<T> predicate) {
            Map<NamespacedKey, T> m = new LinkedHashMap<>();
            for (T t : type.getEnumConstants()) if (predicate.test(t)) m.put(t.getKey(), t);
            this.type = type;
            this.map = Collections.unmodifiableMap(m);
        }

        @Override
        public T get(NamespacedKey key) { return map.get(key); }

        @Override
        public NamespacedKey getKey(T value) { return value.getKey(); }

        @Override
        public Stream<T> stream() { return StreamSupport.stream(spliterator(), false); }

        @Override
        public Iterator<T> iterator() { return map.values().iterator(); }

        public Class<T> getType() { return type; }
    }
}
