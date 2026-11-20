package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.domi.furnace.api.Furnace;

/**
 * Registry.TRIM_MATERIAL·TRIM_PATTERN: 바닐라 1.21.1 값은 처음부터, 데이터 팩이 더한 값은 처음 찾을 때 만든다. 키마다 한 객체라 {@code ==} 가 된다
 * (ArmorTrim 의 같음이 이 객체 동일성이다). 돌 때는 서버 레지스트리 순서(서버가 없거나 비었으면 바닐라 목록). 아무 스레드에서 불러도 된다.
 */
public final class TrimRegistry<T extends Keyed> implements Registry<T> {

    static final List<String> VANILLA_MATERIALS = List.of("amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite", "quartz", "redstone");
    static final List<String> VANILLA_PATTERNS = List.of("bolt", "coast", "dune", "eye", "flow", "host", "raiser", "rib", "sentry", "shaper", "silence",
            "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild");

    private final String gameRegistry;
    private final String name;
    private final List<String> vanilla;
    private final Function<NamespacedKey, T> factory;
    private final Map<NamespacedKey, T> byKey = new ConcurrentHashMap<>();

    TrimRegistry(String gameRegistry, String name, List<String> vanilla, Function<NamespacedKey, T> factory) {
        this.gameRegistry = gameRegistry;
        this.name = name;
        this.vanilla = vanilla;
        this.factory = factory;
        for (String k : vanilla) {
            NamespacedKey key = NamespacedKey.minecraft(k);
            byKey.put(key, factory.apply(key));
        }
    }

    public static TrimRegistry<org.bukkit.inventory.meta.trim.TrimMaterial> materials() {
        return new TrimRegistry<>(org.domi.furnace.api.registry.GameRegistries.TRIM_MATERIAL, "TrimMaterial", VANILLA_MATERIALS,
                org.bukkit.craftbukkit.inventory.trim.CraftTrimMaterial::new);
    }

    public static TrimRegistry<org.bukkit.inventory.meta.trim.TrimPattern> patterns() {
        return new TrimRegistry<>(org.domi.furnace.api.registry.GameRegistries.TRIM_PATTERN, "TrimPattern", VANILLA_PATTERNS,
                org.bukkit.craftbukkit.inventory.trim.CraftTrimPattern::new);
    }

    @Override
    public T get(NamespacedKey key) {
        if (key == null) return null;
        T t = byKey.get(key);
        if (t != null) return t;
        Furnace f = Furnace.serverOrNull();
        if (f == null || !f.gameRegistries().contains(gameRegistry, key.toString())) return null;
        return byKey.computeIfAbsent(key, factory);
    }

    @Override
    public Iterator<T> iterator() {
        Furnace f = Furnace.serverOrNull();
        List<T> out = new ArrayList<>();
        if (f != null) {
            for (String id : f.gameRegistries().entries(gameRegistry)) {
                NamespacedKey key = NamespacedKey.fromString(id);
                T t = key == null ? null : get(key);
                if (t != null) out.add(t);
            }
        }
        if (out.isEmpty()) {
            for (String k : vanilla) out.add(byKey.get(NamespacedKey.minecraft(k)));
        }
        return java.util.Collections.unmodifiableList(out).iterator();
    }

    @Override
    public Stream<T> stream() {
        List<T> all = new ArrayList<>();
        iterator().forEachRemaining(all::add);
        return all.stream();
    }

    @Override
    public String toString() { return "Registry[" + name + "]"; }
}
