// Bukkit PersistentDataContainer 를 Furnace 영속 데이터 위에 덮어씌우는 층 — 읽기는 원본(읽기 전용 보기)을 보고, 쓰기는 모아 두었다가
// 아이템 메타를 ItemStack 에 돌려놓을 때(applyTo) 한꺼번에 적용한다. 원본이 없으면(새 컨테이너·중첩) 메모리 안 값만 쓴다.
package org.domi.furnace.compat.paper;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.domi.furnace.api.persistence.PersistentDataView;

final class ItemPdc implements PersistentDataContainer, PersistentDataAdapterContext {

    /** 바뀐 값: Furnace 종류와 기본 종류 값. 지운 키는 null. */
    private record Value(org.domi.furnace.api.persistence.PersistentDataType<?> type, Object value) {}

    private static final org.domi.furnace.api.persistence.PersistentDataType<?>[] PROBE = {
        org.domi.furnace.api.persistence.PersistentDataType.BYTE, org.domi.furnace.api.persistence.PersistentDataType.SHORT,
        org.domi.furnace.api.persistence.PersistentDataType.INTEGER, org.domi.furnace.api.persistence.PersistentDataType.LONG,
        org.domi.furnace.api.persistence.PersistentDataType.FLOAT, org.domi.furnace.api.persistence.PersistentDataType.DOUBLE,
        org.domi.furnace.api.persistence.PersistentDataType.STRING, org.domi.furnace.api.persistence.PersistentDataType.BYTE_ARRAY,
        org.domi.furnace.api.persistence.PersistentDataType.INTEGER_ARRAY, org.domi.furnace.api.persistence.PersistentDataType.LONG_ARRAY,
        org.domi.furnace.api.persistence.PersistentDataType.TAG_CONTAINER};

    private final PersistentDataView view;
    private final Map<String, Value> changes = new LinkedHashMap<>();

    ItemPdc(PersistentDataView view) { this.view = view; }

    ItemPdc copy() {
        ItemPdc c = new ItemPdc(view);
        for (var e : changes.entrySet()) c.changes.put(e.getKey(), e.getValue() == null ? null : copyValue(e.getValue()));
        return c;
    }

    private static Value copyValue(Value v) {
        Object o = v.value();
        if (o instanceof ItemPdc p) o = p.copy();
        else if (o instanceof byte[] a) o = a.clone();
        else if (o instanceof int[] a) o = a.clone();
        else if (o instanceof long[] a) o = a.clone();
        return new Value(v.type(), o);
    }

    boolean dirty() { return !changes.isEmpty(); }

    // ------------------------------------------------------------------ 종류 대응

    private static org.domi.furnace.api.persistence.PersistentDataType<?> furnaceType(Class<?> primitive) {
        if (primitive == Byte.class) return org.domi.furnace.api.persistence.PersistentDataType.BYTE;
        if (primitive == Short.class) return org.domi.furnace.api.persistence.PersistentDataType.SHORT;
        if (primitive == Integer.class) return org.domi.furnace.api.persistence.PersistentDataType.INTEGER;
        if (primitive == Long.class) return org.domi.furnace.api.persistence.PersistentDataType.LONG;
        if (primitive == Float.class) return org.domi.furnace.api.persistence.PersistentDataType.FLOAT;
        if (primitive == Double.class) return org.domi.furnace.api.persistence.PersistentDataType.DOUBLE;
        if (primitive == String.class) return org.domi.furnace.api.persistence.PersistentDataType.STRING;
        if (primitive == byte[].class) return org.domi.furnace.api.persistence.PersistentDataType.BYTE_ARRAY;
        if (primitive == int[].class) return org.domi.furnace.api.persistence.PersistentDataType.INTEGER_ARRAY;
        if (primitive == long[].class) return org.domi.furnace.api.persistence.PersistentDataType.LONG_ARRAY;
        if (PersistentDataContainer.class.isAssignableFrom(primitive)) return org.domi.furnace.api.persistence.PersistentDataType.TAG_CONTAINER;
        throw new IllegalArgumentException("기본 종류로 저장할 수 없다: " + primitive.getName());
    }

    private static PersistentDataType<?, ?> bukkitType(org.domi.furnace.api.persistence.PersistentDataType<?> t) {
        if (t == org.domi.furnace.api.persistence.PersistentDataType.BYTE) return PersistentDataType.BYTE;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.SHORT) return PersistentDataType.SHORT;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.INTEGER) return PersistentDataType.INTEGER;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.LONG) return PersistentDataType.LONG;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.FLOAT) return PersistentDataType.FLOAT;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.DOUBLE) return PersistentDataType.DOUBLE;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.STRING) return PersistentDataType.STRING;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.BYTE_ARRAY) return PersistentDataType.BYTE_ARRAY;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.INTEGER_ARRAY) return PersistentDataType.INTEGER_ARRAY;
        if (t == org.domi.furnace.api.persistence.PersistentDataType.LONG_ARRAY) return PersistentDataType.LONG_ARRAY;
        return PersistentDataType.TAG_CONTAINER;
    }

    /** 키가 뜻하는 값(바뀐 것 → 원본). 없으면 null. 원본 값의 종류는 하나씩 읽어 보며 찾는다. 알 수 없는 종류(목록)는 null. */
    private Value lookup(String key) {
        if (changes.containsKey(key)) return changes.get(key);
        if (view == null || !view.has(key)) return null;
        for (var t : PROBE) {
            if (view.has(key, t)) {
                Object v = view.get(key, t);
                if (t == org.domi.furnace.api.persistence.PersistentDataType.TAG_CONTAINER) v = detached((PersistentDataView) v);
                return new Value(t, v);
            }
        }
        return null;
    }

    /** 중첩 컨테이너를 메모리로 복사한다 — 원본(엔티티 콜백 안의 살아 있는 값)을 콜백 밖으로 들고 나가지 않게. */
    private static ItemPdc detached(PersistentDataView nested) {
        ItemPdc copy = new ItemPdc(null);
        ItemPdc src = new ItemPdc(nested);
        for (String k : nested.keys()) {
            Value v = src.lookup(k);
            if (v != null) copy.changes.put(k, copyValue(v));
        }
        return copy;
    }

    private boolean exists(String key) {
        if (changes.containsKey(key)) return changes.get(key) != null;
        return view != null && view.has(key);
    }

    // ------------------------------------------------------------------ Bukkit PersistentDataContainer

    @Override
    public <P, C> void set(NamespacedKey key, PersistentDataType<P, C> type, C value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(value, "value");
        P prim = type.toPrimitive(value, this);
        changes.put(key.toString(), new Value(furnaceType(type.getPrimitiveType()), prim));
    }

    @Override
    public <P, C> boolean has(NamespacedKey key, PersistentDataType<P, C> type) {
        Value v = lookup(key.toString());
        return v != null && v.type().equals(furnaceType(type.getPrimitiveType()));
    }

    @Override
    public boolean has(NamespacedKey key) { return exists(key.toString()); }

    @SuppressWarnings("unchecked")
    @Override
    public <P, C> C get(NamespacedKey key, PersistentDataType<P, C> type) {
        Value v = lookup(key.toString());
        if (v == null) {
            if (exists(key.toString())) throw new IllegalArgumentException("The found object is of a type Furnace's compat layer does not read (" + key + ")");
            return null;
        }
        if (!v.type().equals(furnaceType(type.getPrimitiveType()))) {
            throw new IllegalArgumentException("The found object is of the type " + v.type() + ", which is not the expected " + type.getPrimitiveType().getSimpleName());
        }
        return type.fromPrimitive((P) v.value(), this);
    }

    @Override
    public <P, C> C getOrDefault(NamespacedKey key, PersistentDataType<P, C> type, C defaultValue) {
        C v = get(key, type);
        return v != null ? v : defaultValue;
    }

    @Override
    public Set<NamespacedKey> getKeys() {
        Set<String> keys = new LinkedHashSet<>();
        if (view != null) keys.addAll(view.keys());
        for (var e : changes.entrySet()) {
            if (e.getValue() == null) keys.remove(e.getKey());
            else keys.add(e.getKey());
        }
        Set<NamespacedKey> out = new LinkedHashSet<>();
        for (String k : keys) {
            NamespacedKey nk = NamespacedKey.fromString(k);
            if (nk != null) out.add(nk);
        }
        return out;
    }

    @Override
    public void remove(NamespacedKey key) {
        Objects.requireNonNull(key, "key");
        if (exists(key.toString())) changes.put(key.toString(), null);
    }

    @Override
    public boolean isEmpty() {
        if (view != null) {
            for (String k : view.keys()) if (!changes.containsKey(k) || changes.get(k) != null) return false;
        }
        for (var e : changes.entrySet()) if (e.getValue() != null) return false;
        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public void copyTo(PersistentDataContainer other, boolean replace) {
        Objects.requireNonNull(other, "other");
        for (NamespacedKey k : getKeys()) {
            if (!replace && other.has(k)) continue;
            Value v = lookup(k.toString());
            if (v == null) throw new org.domi.furnace.compat.paper.UnsupportedFeature("PersistentDataContainer#copyTo(" + k + ")", "이 키의 값 종류(목록 등)를 호환 계층이 아직 읽지 못한다");
            other.set(k, (PersistentDataType) bukkitType(v.type()), v.value());
        }
    }

    @Override
    public PersistentDataAdapterContext getAdapterContext() { return this; }

    @Override
    public PersistentDataContainer newPersistentDataContainer() { return new ItemPdc(null); }

    // ------------------------------------------------------------------ Furnace 로 쓰기

    /** 바뀐 것을 Furnace 컨테이너에 적용한다(ItemStack#withPersistentData 콜백 안에서). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void applyTo(org.domi.furnace.api.persistence.PersistentDataContainer target) {
        for (var e : changes.entrySet()) {
            Value v = e.getValue();
            if (v == null) {
                target.remove(e.getKey());
                continue;
            }
            Object o = v.value();
            if (o instanceof PersistentDataContainer nested) {
                var child = target.newContainer();
                ((ItemPdc) (nested instanceof ItemPdc ip ? ip : copyNested(nested))).applyTo(child);
                o = child;
            }
            target.set(e.getKey(), (org.domi.furnace.api.persistence.PersistentDataType) v.type(), o);
        }
    }

    /** 사용자가 직접 구현한 컨테이너(드묾)는 복사해서 쓴다. */
    private static ItemPdc copyNested(PersistentDataContainer other) {
        ItemPdc c = new ItemPdc(null);
        other.copyTo(c, true);
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ItemPdc p)) return false;
        Set<NamespacedKey> a = getKeys();
        if (!a.equals(p.getKeys())) return false;
        for (NamespacedKey k : a) {
            Value x = lookup(k.toString()), y = p.lookup(k.toString());
            if (x == null || y == null) return false;
            if (!x.type().equals(y.type()) || !valueEquals(x.value(), y.value())) return false;
        }
        return true;
    }

    private static boolean valueEquals(Object a, Object b) {
        if (a instanceof byte[] x && b instanceof byte[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof int[] x && b instanceof int[] y) return java.util.Arrays.equals(x, y);
        if (a instanceof long[] x && b instanceof long[] y) return java.util.Arrays.equals(x, y);
        return Objects.equals(a, b);
    }

    @Override
    public int hashCode() { return getKeys().hashCode(); }
}
