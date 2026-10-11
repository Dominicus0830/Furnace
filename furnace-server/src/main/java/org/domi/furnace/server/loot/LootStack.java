// 전리품이 만드는 아이템 스택(바닐라 ItemStack 의 전리품 쪽 부분) — 아이템 이름, 개수, 컴포넌트 패치.
package org.domi.furnace.server.loot;

import java.util.LinkedHashMap;
import java.util.Map;

public final class LootStack {

    public static final String AIR = "minecraft:air";

    private final String item;
    private int count;
    private final LinkedHashMap<String, Object> patch;

    public LootStack(String item, int count) { this(item, count, new LinkedHashMap<>()); }

    private LootStack(String item, int count, LinkedHashMap<String, Object> patch) {
        this.item = item;
        this.count = count;
        this.patch = patch;
    }

    public static LootStack empty() { return new LootStack(AIR, 0); }

    public String item() { return item; }

    public boolean is(String other) { return item.equals(other); }

    public int count() { return isEmpty() ? 0 : count; }

    public boolean isEmpty() { return item.equals(AIR) || count <= 0; }

    public void setCount(int c) { count = c; }

    public void grow(int n) { setCount(count + n); }

    public void limitSize(int max) { if (!isEmpty() && count > max) setCount(max); }

    /** 기본값 대비 바뀐 컴포넌트(바닐라 DataComponentPatch — 제거 표시는 null 값). */
    public Map<String, Object> patch() { return patch; }

    /** 바닐라 DataComponentPatch 코덱 모양 — 제거는 "!id": {}. */
    public Map<String, Object> encodedPatch() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : patch.entrySet()) {
            if (e.getValue() == null) out.put("!" + e.getKey(), new LinkedHashMap<>());
            else out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    public LootStack copy() { return isEmpty() ? empty() : new LootStack(item, count, deepCopy(patch)); }

    public LootStack copyWithCount(int n) {
        if (isEmpty()) return empty();
        LootStack c = copy();
        c.setCount(n);
        return c;
    }

    /** 바닐라 split: min(n, count)개를 떼어 낸 복사본을 돌려준다. */
    public LootStack split(int n) {
        int k = Math.min(n, count());
        LootStack c = copyWithCount(k);
        if (!isEmpty()) count -= k;
        return c;
    }

    /** 다른 아이템으로 바꾼 복사본(바닐라 transmuteCopy — 패치는 유지). */
    public LootStack transmute(String newItem) {
        if (isEmpty()) return empty();
        return new LootStack(newItem, count, deepCopy(patch));
    }

    public Object get(String component) {
        if (patch.containsKey(component)) return patch.get(component);
        return ItemData.vanilla().defaultComponent(item, component);
    }

    public boolean has(String component) { return get(component) != null; }

    /** 바닐라 PatchedDataComponentMap.set — 기본값과 같으면 패치에서 뺀다. */
    public void set(String component, Object value) {
        Object def = ItemData.vanilla().defaultComponent(item, component);
        if (Components.same(def, value)) patch.remove(component);
        else patch.put(component, value);
    }

    /** 바닐라 remove: 기본값이 있으면 제거 표시, 없으면 패치에서 뺀다. 이전 값을 돌려준다. */
    public Object remove(String component) {
        Object old = get(component);
        if (ItemData.vanilla().defaultComponent(item, component) != null) patch.put(component, null);
        else patch.remove(component);
        return old;
    }

    public int maxStackSize() {
        Object v = get("minecraft:max_stack_size");
        return v instanceof Number n ? n.intValue() : 1;
    }

    /** 바닐라 isDamageableItem: max_damage·damage 가 있고 unbreakable 이 없다. */
    public boolean isDamageable() {
        return get("minecraft:max_damage") != null && !has("minecraft:unbreakable") && get("minecraft:damage") != null;
    }

    public int maxDamage() { return get("minecraft:max_damage") instanceof Number n ? n.intValue() : 0; }

    public int damage() { return get("minecraft:damage") instanceof Number n ? Mth.clamp(n.intValue(), 0, maxDamage()) : 0; }

    public void setDamage(int d) { set("minecraft:damage", Mth.clamp(d, 0, maxDamage())); }

    @SuppressWarnings("unchecked")
    static <T> T deepCopy(T v) {
        if (v instanceof Map<?, ?> m) {
            LinkedHashMap<String, Object> c = new LinkedHashMap<>();
            for (var e : m.entrySet()) c.put((String) e.getKey(), deepCopy(e.getValue()));
            return (T) c;
        }
        if (v instanceof java.util.List<?> l) {
            java.util.ArrayList<Object> c = new java.util.ArrayList<>();
            for (Object x : l) c.add(deepCopy(x));
            return (T) c;
        }
        return v;
    }

    @Override
    public String toString() { return count + " " + item + (patch.isEmpty() ? "" : " " + Components.canon(patch)); }
}
