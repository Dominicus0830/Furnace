package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.banner.PatternType;
import org.bukkit.craftbukkit.block.banner.CraftPatternType;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.registry.GameRegistries;

/**
 * Registry.BANNER_PATTERN: 바닐라 1.21.1 무늬 43개는 처음부터(PatternType 상수 차례 = ordinal 0..42), 데이터 팩이 더한 무늬는 처음 찾을 때 만든다.
 * 키마다 한 객체라 {@code ==} 가 된다. 돌 때(iterator)는 서버 레지스트리 순서(서버가 없으면 바닐라 목록). 아무 스레드에서 불러도 된다.
 */
public final class BannerPatternRegistry implements Registry<PatternType> {

    /** 바닐라 1.21.1 무늬, PatternType 상수 순서(Paper 는 그 인터페이스가 초기화될 때 이 순서로 만든다). */
    static final List<String> VANILLA = List.of("base", "square_bottom_left", "square_bottom_right", "square_top_left", "square_top_right",
            "stripe_bottom", "stripe_top", "stripe_left", "stripe_right", "stripe_center", "stripe_middle", "stripe_downright", "stripe_downleft",
            "small_stripes", "cross", "straight_cross", "triangle_bottom", "triangle_top", "triangles_bottom", "triangles_top", "diagonal_left",
            "diagonal_up_right", "diagonal_up_left", "diagonal_right", "circle", "rhombus", "half_vertical", "half_horizontal",
            "half_vertical_right", "half_horizontal_bottom", "border", "curly_border", "creeper", "gradient", "gradient_up", "bricks", "skull",
            "flower", "mojang", "globe", "piglin", "flow", "guster");

    private final Map<NamespacedKey, PatternType> byKey = new ConcurrentHashMap<>();
    private final AtomicInteger nextOrdinal = new AtomicInteger();

    public BannerPatternRegistry() {
        for (String k : VANILLA) {
            NamespacedKey key = NamespacedKey.minecraft(k);
            byKey.put(key, new CraftPatternType(key, nextOrdinal.getAndIncrement()));
        }
    }

    @Override
    public PatternType get(NamespacedKey key) {
        if (key == null) return null;
        PatternType t = byKey.get(key);
        if (t != null) return t;
        Furnace f = Furnace.serverOrNull();
        if (f == null || !f.gameRegistries().contains(GameRegistries.BANNER_PATTERN, key.toString())) return null;
        return byKey.computeIfAbsent(key, k -> new CraftPatternType(k, nextOrdinal.getAndIncrement()));
    }

    @Override
    public Iterator<PatternType> iterator() {
        Furnace f = Furnace.serverOrNull();
        List<PatternType> out = new ArrayList<>();
        if (f == null) {
            for (String k : VANILLA) out.add(byKey.get(NamespacedKey.minecraft(k)));
        } else {
            for (String id : f.gameRegistries().entries(GameRegistries.BANNER_PATTERN)) {
                NamespacedKey key = NamespacedKey.fromString(id);
                PatternType t = key == null ? null : get(key);
                if (t != null) out.add(t);
            }
        }
        return java.util.Collections.unmodifiableList(out).iterator();
    }

    @Override
    public Stream<PatternType> stream() {
        List<PatternType> all = new ArrayList<>();
        iterator().forEachRemaining(all::add);
        return all.stream();
    }

    @Override
    public String toString() { return "Registry[PatternType]"; }
}
