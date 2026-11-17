// 플러그인 영속 데이터를 NBT compound 위에 구현한 것. 엔티티의 BukkitValues·청크의 ChunkBukkitValues 를 그대로 감싼다 —
// 모르는 꼴의 값(다른 서버 플러그인이 쓴 목록 등)은 건드리지 않고 보존한다. readOnly 면 쓰기를 거부한다(핸들 사본).
package org.domi.furnace.server.plugin;

import java.util.LinkedHashSet;
import java.util.Set;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtTags;

final class NbtPersistentData implements PersistentDataContainer {

    static final NbtPersistentData EMPTY = new NbtPersistentData(new NbtCompound(), true);

    private final NbtCompound tag;
    private final boolean readOnly;

    NbtPersistentData(NbtCompound tag, boolean readOnly) {
        this.tag = tag;
        this.readOnly = readOnly;
    }

    /** 사본 보기(src 가 null 이면 빈 것). */
    static NbtPersistentData snapshot(NbtCompound src) {
        return src == null || src.tags().isEmpty() ? EMPTY : new NbtPersistentData(src.copy(), true);
    }

    NbtCompound tag() { return tag; }

    @Override
    public <T> boolean has(String key, PersistentDataType<T> type) {
        Object v = tag.tags().get(PersistentDataType.checkKey(key));
        return v != null && convert(v, type) != null;
    }

    @Override
    public Object raw(String key) {
        Object v = tag.tags().get(PersistentDataType.checkKey(key));
        return v == null ? null : toRaw(v);
    }

    /** 저장된 NBT 값 → 종류 없는 값(배열·중첩 컨테이너는 사본, 목록은 새 목록). */
    private Object toRaw(Object v) {
        if (v instanceof NbtList l) {
            java.util.List<Object> out = new java.util.ArrayList<>(l.size());
            for (Object item : l.items()) out.add(toRaw(item));
            return out;
        }
        if (v instanceof NbtCompound c) return new NbtPersistentData(c.copy(), true);
        if (v instanceof byte[] a) return a.clone();
        if (v instanceof int[] a) return a.clone();
        if (v instanceof long[] a) return a.clone();
        return v;
    }

    @Override
    public boolean has(String key) { return tag.tags().containsKey(PersistentDataType.checkKey(key)); }

    @Override
    public <T> T get(String key, PersistentDataType<T> type) {
        Object v = tag.tags().get(PersistentDataType.checkKey(key));
        if (v == null) return null;
        T out = convert(v, type);
        if (out == null) throw new IllegalArgumentException("the value of " + key + " is not a " + type);
        return out;
    }

    /** 저장된 NBT 값 → API 값. 종류가 맞지 않으면 null. 배열은 사본. */
    @SuppressWarnings("unchecked")
    private <T> T convert(Object v, PersistentDataType<T> type) {
        PersistentDataType<?> elem = type.elementType();
        if (elem != null) {
            if (!(v instanceof NbtList l)) return null;
            java.util.List<Object> out = new java.util.ArrayList<>(l.size());
            for (Object item : l.items()) {
                Object c = convert(item, elem);
                if (c == null) return null;
                out.add(c);
            }
            return (T) java.util.Collections.unmodifiableList(out);
        }
        if (type == PersistentDataType.BOOLEAN) return v instanceof Byte b ? (T) Boolean.valueOf(b != 0) : null;
        if (type == PersistentDataType.TAG_CONTAINER) return v instanceof NbtCompound c ? (T) new NbtPersistentData(readOnly ? c.copy() : c, readOnly) : null;
        if (v instanceof byte[] a) return type == PersistentDataType.BYTE_ARRAY ? (T) a.clone() : null;
        if (v instanceof int[] a) return type == PersistentDataType.INTEGER_ARRAY ? (T) a.clone() : null;
        if (v instanceof long[] a) return type == PersistentDataType.LONG_ARRAY ? (T) a.clone() : null;
        return type.javaType().isInstance(v) ? (T) v : null;
    }

    /** API 값 → NBT 값. 종류에 맞지 않으면 IllegalArgumentException. */
    private static Object toNbt(Object value, PersistentDataType<?> type) {
        PersistentDataType<?> elem = type.elementType();
        if (elem != null) {
            if (!(value instanceof java.util.List<?> list)) throw new IllegalArgumentException(value.getClass().getSimpleName() + " is not a " + type + " value");
            NbtList out = new NbtList(NbtIo.TAG_END);
            for (Object item : list) {
                if (item == null) throw new IllegalArgumentException("a list cannot contain null");
                Object nbt = toNbt(item, elem);
                if (!out.addTag(out.size(), nbt, NbtTags.typeOf(nbt))) throw new IllegalArgumentException("the list elements have mixed types");
            }
            return out;
        }
        if (type == PersistentDataType.BOOLEAN) {
            if (!(value instanceof Boolean b)) throw new IllegalArgumentException(value.getClass().getSimpleName() + " is not a " + type + " value");
            return (byte) (b ? 1 : 0);
        }
        if (type == PersistentDataType.TAG_CONTAINER) {
            if (!(value instanceof NbtPersistentData c)) throw new IllegalArgumentException("nested containers must be created with newContainer()");
            return c.tag.copy();
        }
        if (value instanceof byte[] a) return type == PersistentDataType.BYTE_ARRAY ? a.clone() : mismatch(value, type);
        if (value instanceof int[] a) return type == PersistentDataType.INTEGER_ARRAY ? a.clone() : mismatch(value, type);
        if (value instanceof long[] a) return type == PersistentDataType.LONG_ARRAY ? a.clone() : mismatch(value, type);
        if (type.javaType().isInstance(value)) return value;
        return mismatch(value, type);
    }

    private static Object mismatch(Object value, PersistentDataType<?> type) {
        throw new IllegalArgumentException(value.getClass().getSimpleName() + " is not a " + type + " value");
    }

    @Override
    public Set<String> keys() { return new LinkedHashSet<>(tag.tags().keySet()); }

    @Override
    public boolean isEmpty() { return tag.tags().isEmpty(); }

    private void writable() {
        if (readOnly) throw new UnsupportedOperationException("this is a read-only copy - edit it inside editPersistentData");
    }

    @Override
    public <T> void set(String key, PersistentDataType<T> type, T value) {
        writable();
        PersistentDataType.checkKey(key);
        if (value == null) throw new IllegalArgumentException("the value is null - use remove to delete");
        tag.tags().put(key, toNbt(value, type));
    }

    @Override
    public void setRaw(String key, Object value) {
        writable();
        PersistentDataType.checkKey(key);
        if (value == null) throw new IllegalArgumentException("the value is null - use remove to delete");
        tag.tags().put(key, rawToNbt(value));
    }

    /** 종류 없는 값 → NBT 값(목록은 모양대로 — 안쪽 목록마다 자기 원소 종류). */
    private static Object rawToNbt(Object v) {
        if (v instanceof java.util.List<?> list) {
            NbtList out = new NbtList(NbtIo.TAG_END);
            for (Object item : list) {
                if (item == null) throw new IllegalArgumentException("a list cannot contain null");
                Object nbt = rawToNbt(item);
                if (!out.addTag(out.size(), nbt, NbtTags.typeOf(nbt))) throw new IllegalArgumentException("the list elements have mixed types");
            }
            return out;
        }
        if (v instanceof NbtPersistentData c) return c.tag.copy();
        if (v instanceof Boolean b) return (byte) (b ? 1 : 0);
        if (v instanceof byte[] a) return a.clone();
        if (v instanceof int[] a) return a.clone();
        if (v instanceof long[] a) return a.clone();
        if (v instanceof Byte || v instanceof Short || v instanceof Integer || v instanceof Long || v instanceof Float || v instanceof Double || v instanceof String) return v;
        throw new IllegalArgumentException(v.getClass().getSimpleName() + " is not a persistent data value");
    }

    @Override
    public void remove(String key) {
        writable();
        tag.tags().remove(PersistentDataType.checkKey(key));
    }

    @Override
    public PersistentDataContainer newContainer() { return new NbtPersistentData(new NbtCompound(), false); }
}
