package org.domi.furnace.compat.paper;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.bukkit.Art;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

/** Bukkit Registry 의 호환 쪽 도우미 — 열거형이 아닌 값(마법부여·상태 효과)의 레지스트리와 Bukkit.getRegistry(Class) 의 답. */
public final class CompatRegistries {

    private CompatRegistries() {}

    /** 고정 값 목록의 레지스트리(키 순서 = 주어진 순서). 플러그인에게는 Paper 와 같은 CraftRegistry 로. */
    public static <T extends Keyed> Registry<T> fixed(Class<T> type, Iterable<T> values) { return craft(new Fixed<>(type, values)); }

    /** 고정 값 목록의 레지스트리 — Paper 1.21.1 에서 부트스트랩 때 고칠 수 있는 것(마법부여)은 WritableCraftRegistry. */
    public static <T extends Keyed> Registry<T> fixedWritable(Class<T> type, Iterable<T> values) { return CraftAccess.newWritableRegistry(new Fixed<>(type, values)); }

    /** 서버 레지스트리에서 오는 Bukkit 레지스트리의 몸을 Paper 와 같은 CraftRegistry 로 감싼다(T-264 P2d). */
    public static <T extends Keyed> Registry<T> craft(Registry<T> body) { return CraftAccess.newRegistry(body); }

    /** 이 호환 계층에 있는 레지스트리(없으면 null — Bukkit 도 모르는 형이면 null). */
    @SuppressWarnings("unchecked")
    public static <T extends Keyed> Registry<T> forType(Class<T> type) {
        if (type == null) throw new IllegalArgumentException("type");
        Registry<?> r;
        if (type == Art.class) r = Registry.ART;
        else if (type == Attribute.class) r = Registry.ATTRIBUTE;
        else if (type == org.bukkit.block.banner.PatternType.class) r = Registry.BANNER_PATTERN;
        else if (type == Biome.class) r = Registry.BIOME;
        else if (type == Enchantment.class) r = Registry.ENCHANTMENT;
        else if (type == EntityType.class) r = Registry.ENTITY_TYPE;
        else if (type == Material.class) r = Registry.MATERIAL;
        else if (type == PotionEffectType.class) r = Registry.EFFECT;
        else if (type == Particle.class) r = Registry.PARTICLE_TYPE;
        else if (type == PotionType.class) r = Registry.POTION;
        else if (type == Sound.class) r = Registry.SOUNDS;
        else if (type == org.bukkit.inventory.meta.trim.TrimMaterial.class) r = Registry.TRIM_MATERIAL;
        else if (type == org.bukkit.inventory.meta.trim.TrimPattern.class) r = Registry.TRIM_PATTERN;
        else r = null;
        return (Registry<T>) r;
    }

    private static final class Fixed<T extends Keyed> implements Registry<T> {

        private final Class<T> type;
        private final Map<NamespacedKey, T> map;

        Fixed(Class<T> type, Iterable<T> values) {
            Map<NamespacedKey, T> m = new LinkedHashMap<>();
            for (T t : values) m.put(Objects.requireNonNull(t.getKey()), t);
            this.type = type;
            this.map = Collections.unmodifiableMap(m);
        }

        @Override
        public T get(NamespacedKey key) { return map.get(key); }

        @Override
        public Stream<T> stream() { return StreamSupport.stream(spliterator(), false); }

        @Override
        public Iterator<T> iterator() { return map.values().iterator(); }

        @Override
        public String toString() { return "Registry[" + type.getSimpleName() + "]"; }
    }
}
