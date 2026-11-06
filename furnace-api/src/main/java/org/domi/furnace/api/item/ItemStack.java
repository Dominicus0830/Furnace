// 아이템 스택 — 불변 값 객체. 종류는 레지스트리 키, 컴포넌트는 id → SNBT 문자열(기본값과 다른 것만).
package org.domi.furnace.api.item;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public record ItemStack(String type, int amount, Map<String, String> components, Set<String> removedComponents) {

    public static final ItemStack EMPTY = new ItemStack("minecraft:air", 0, Map.of(), Set.of());

    public ItemStack {
        Objects.requireNonNull(type, "type");
        if (type.indexOf(':') < 0) type = "minecraft:" + type;
        if (amount < 0) throw new IllegalArgumentException("amount 는 0 이상: " + amount);
        components = components.isEmpty() ? Map.of() : java.util.Collections.unmodifiableMap(new TreeMap<>(components));
        removedComponents = removedComponents.isEmpty() ? Set.of() : java.util.Collections.unmodifiableSet(new TreeSet<>(removedComponents));
    }

    public ItemStack(String type, int amount) { this(type, amount, Map.of(), Set.of()); }

    public static ItemStack of(String type) { return new ItemStack(type, 1); }

    public boolean isEmpty() { return amount <= 0 || type.equals("minecraft:air"); }

    public ItemStack withAmount(int n) { return n <= 0 ? EMPTY : new ItemStack(type, n, components, removedComponents); }

    public ItemStack withComponent(String id, String snbt) {
        Objects.requireNonNull(snbt, "snbt");
        String key = key(id);
        TreeMap<String, String> m = new TreeMap<>(components);
        m.put(key, snbt);
        TreeSet<String> r = new TreeSet<>(removedComponents);
        r.remove(key);
        return new ItemStack(type, amount, m, r);
    }

    public ItemStack withoutComponent(String id) {
        String key = key(id);
        TreeMap<String, String> m = new TreeMap<>(components);
        m.remove(key);
        TreeSet<String> r = new TreeSet<>(removedComponents);
        r.add(key);
        return new ItemStack(type, amount, m, r);
    }

    private static String key(String id) {
        Objects.requireNonNull(id, "id");
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** 종류와 컴포넌트가 같은가(개수 무시) — 바닐라 isSameItemSameComponents. */
    public boolean isSimilar(ItemStack o) {
        return type.equals(o.type) && components.equals(o.components) && removedComponents.equals(o.removedComponents);
    }

    /** minecraft:damage 컴포넌트(정수 SNBT). 없거나 읽지 못하면 0. */
    public int damage() {
        String s = components.get("minecraft:damage");
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
