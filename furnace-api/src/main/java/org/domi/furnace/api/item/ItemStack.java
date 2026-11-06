// 아이템 스택 — 불변 값 객체. 종류는 레지스트리 키, 컴포넌트는 id → SNBT 문자열(기본값과 다른 것만).
package org.domi.furnace.api.item;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Consumer;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataView;

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

    // ------------------------------------------------------------------ 타입 있는 컴포넌트·영속 데이터(서버 안에서만)

    /** 컴포넌트의 실제 값(붙은 것 → 아이템 기본값). 없거나 지워졌으면 null. */
    public <T> T get(ItemComponent<T> component) { return ItemSupport.get().get(this, component); }

    public <T> T getOrDefault(ItemComponent<T> component, T fallback) {
        T v = get(component);
        return v != null ? v : fallback;
    }

    /** 컴포넌트가 실제로 있는가(기본값 포함). */
    public boolean has(ItemComponent<?> component) { return get(component) != null; }

    /** 값을 넣은 새 스택. 값이 null 이면 IllegalArgumentException(지우려면 without). */
    public <T> ItemStack with(ItemComponent<T> component, T value) {
        if (value == null) throw new IllegalArgumentException("값이 null — 지우려면 without");
        return ItemSupport.get().set(this, component, value);
    }

    /** 값 없는 표시 컴포넌트(hide_tooltip 등)를 붙인 새 스택. */
    public ItemStack with(ItemComponent<Marker> marker) { return with(marker, Marker.PRESENT); }

    /** 컴포넌트를 없앤 새 스택. */
    public ItemStack without(ItemComponent<?> component) { return ItemSupport.get().remove(this, component); }

    /**
     * 플러그인 영속 데이터(minecraft:custom_data 안 PublicBukkitValues — Paper 와 같은 자리)의 읽기 전용 사본.
     * 아이템은 불변이라 어느 스레드에서 읽어도 된다.
     */
    public PersistentDataView persistentData() { return ItemSupport.get().persistentData(this); }

    /**
     * 영속 데이터를 콜백 안에서 고친 새 스택(이 스택은 그대로). 콜백 밖으로 컨테이너를 들고 나가 쓰지 않는다.
     * 바뀐 것이 없으면 이 스택 그대로를 돌려준다. custom_data 의 다른 값은 건드리지 않는다.
     */
    public ItemStack withPersistentData(Consumer<PersistentDataContainer> edit) { return ItemSupport.get().editPersistentData(this, edit); }
}
