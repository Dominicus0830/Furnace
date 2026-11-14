// Bukkit Tag 의 구현 — Furnace 게임 레지스트리(Furnace.server().gameRegistries())의 태그를 그때그때 읽는다.
// CraftBukkit 의 CraftBlockTag·CraftItemTag·CraftFluidTag·CraftEntityTag·CraftGameEventTag 와 같은 동작: 태그를 잡고 있다가 데이터 팩 리로드로 내용이
// 바뀌면 다음 물음부터 새 내용이 보이고(태그 키를 들고 레지스트리를 매번 본다), 값이 태그에 없거나 그 레지스트리의 값이 아니면(예: 아이템 전용 Material 을 블록 태그에 물음) isTagged 는 false.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import org.bukkit.Fluid;
import org.bukkit.GameEvent;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.entity.EntityType;
import org.domi.furnace.api.Furnace;
import org.domi.furnace.api.registry.GameRegistries;

public final class CompatTags {

    private CompatTags() {}

    /** Bukkit.getTag(registry, key, clazz) — 없는 태그면 null, 모르는 레지스트리면 IllegalArgumentException. */
    @SuppressWarnings("unchecked")
    public static <T extends Keyed> Tag<T> get(String registry, NamespacedKey tag, Class<T> clazz) {
        if (tag == null) throw new IllegalArgumentException("NamespacedKey tag cannot be null");
        Kind kind = kind(registry, clazz);
        Optional<List<String>> values = registries().tag(kind.vanillaRegistry, tag.toString());
        return values.isPresent() ? (Tag<T>) new Impl<>(kind, tag) : null;
    }

    /** Bukkit.getTags(registry, clazz) — 그 레지스트리에 지금 있는 태그 전부. */
    @SuppressWarnings("unchecked")
    public static <T extends Keyed> Iterable<Tag<T>> all(String registry, Class<T> clazz) {
        Kind kind = kind(registry, clazz);
        List<Tag<T>> out = new ArrayList<>();
        for (String id : registries().tags(kind.vanillaRegistry)) {
            NamespacedKey key = NamespacedKey.fromString(id);
            if (key != null) out.add((Tag<T>) new Impl<>(kind, key));
        }
        return Collections.unmodifiableList(out);
    }

    private static GameRegistries registries() { return Furnace.server().gameRegistries(); }

    /** 레지스트리 하나의 값 ↔ 항목 id 변환. */
    private record Kind(String bukkitRegistry, String vanillaRegistry, Class<?> type,
                        Function<NamespacedKey, Keyed> byKey, Predicate<Keyed> inRegistry) {}

    private static Kind kind(String registry, Class<?> clazz) {
        if (registry == null) throw new IllegalArgumentException("registry cannot be null");
        if (clazz == null) throw new IllegalArgumentException("Class clazz cannot be null");
        switch (registry) {
            case Tag.REGISTRY_BLOCKS:
                if (clazz != Material.class) throw new IllegalArgumentException("Block namespace (" + registry + ") must have material type");
                return BLOCKS;
            case Tag.REGISTRY_ITEMS:
                if (clazz != Material.class) throw new IllegalArgumentException("Item namespace (" + registry + ") must have material type");
                return ITEMS;
            case Tag.REGISTRY_FLUIDS:
                if (clazz != Fluid.class) throw new IllegalArgumentException("Fluid namespace (" + registry + ") must have fluid type");
                return FLUIDS;
            case Tag.REGISTRY_ENTITY_TYPES:
                if (clazz != EntityType.class) throw new IllegalArgumentException("Entity namespace (" + registry + ") must have entity type");
                return ENTITY_TYPES;
            case Tag.REGISTRY_GAME_EVENTS:
                if (clazz != GameEvent.class) throw new IllegalArgumentException("GameEvent namespace (" + registry + ") must have GameEvent type");
                return GAME_EVENTS;
            default:
                throw new IllegalArgumentException();
        }
    }

    private static final Kind BLOCKS = new Kind(Tag.REGISTRY_BLOCKS, "minecraft:block", Material.class,
            k -> materialOf(k, true), v -> v instanceof Material m && m.isBlock());
    private static final Kind ITEMS = new Kind(Tag.REGISTRY_ITEMS, "minecraft:item", Material.class,
            k -> materialOf(k, false), v -> v instanceof Material m && m.isItem());
    private static final Kind FLUIDS = new Kind(Tag.REGISTRY_FLUIDS, "minecraft:fluid", Fluid.class, CompatTags::fluidOf, v -> v instanceof Fluid);
    private static final Kind ENTITY_TYPES = new Kind(Tag.REGISTRY_ENTITY_TYPES, "minecraft:entity_type", EntityType.class,
            k -> Registry.ENTITY_TYPE.get(k), v -> v instanceof EntityType t && t != EntityType.UNKNOWN);
    private static final Kind GAME_EVENTS = new Kind(Tag.REGISTRY_GAME_EVENTS, "minecraft:game_event", GameEvent.class,
            GameEvent::getByKey, v -> v instanceof GameEvent);

    private static Material materialOf(NamespacedKey key, boolean block) {
        if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) return null;   // 데이터 팩이 더한 다른 이름공간 항목은 Bukkit 재료로 없다
        Material m = Material.matchMaterial(key.getKey());
        return m != null && (block ? m.isBlock() : m.isItem()) ? m : null;
    }

    private static Fluid fluidOf(NamespacedKey key) {
        if (!NamespacedKey.MINECRAFT.equals(key.getNamespace())) return null;
        for (Fluid f : Fluid.values()) if (f.getKey().equals(key)) return f;
        return null;
    }

    private static final class Impl<T extends Keyed> implements Tag<T> {

        private final Kind kind;
        private final NamespacedKey key;
        /** getValues 가 마지막으로 만든 값 — 같은 항목 목록 객체(레지스트리 보기가 안 바뀜)면 다시 만들지 않는다. */
        private volatile Cache cache;

        private record Cache(List<String> source, Set<?> values) {}

        Impl(Kind kind, NamespacedKey key) {
            this.kind = kind;
            this.key = key;
        }

        @Override
        public NamespacedKey getKey() { return key; }

        @Override
        public boolean isTagged(T item) {
            if (item == null) {
                // Paper: 블록·아이템 태그는 EnumMap 조회라 null 이면 false, 엔티티·유체·게임 이벤트 태그는 예외
                if (kind == BLOCKS || kind == ITEMS) return false;
                throw new IllegalArgumentException("item cannot be null");
            }
            if (!kind.inRegistry.test(item)) return false;
            return registries().isTagged(kind.vanillaRegistry, key.toString(), item.getKey().toString());
        }

        @Override
        @SuppressWarnings("unchecked")
        public Set<T> getValues() {
            List<String> ids = registries().tag(kind.vanillaRegistry, key.toString()).orElse(List.of());
            Cache c = cache;
            if (c != null && c.source == ids) return (Set<T>) c.values;
            Set<T> out = new LinkedHashSet<>();
            for (String id : ids) {
                NamespacedKey k = NamespacedKey.fromString(id);
                Keyed v = k == null ? null : kind.byKey.apply(k);
                if (v != null) out.add((T) v);
            }
            Set<T> frozen = Collections.unmodifiableSet(out);
            cache = new Cache(ids, frozen);
            return frozen;
        }

        @Override
        public boolean equals(Object o) {
            return this == o || o instanceof Impl<?> t && t.kind == kind && t.key.equals(key);
        }

        @Override
        public int hashCode() { return key.hashCode(); }

        @Override
        public String toString() { return "Tag{" + kind.bukkitRegistry + ":" + key + "}"; }
    }
}
