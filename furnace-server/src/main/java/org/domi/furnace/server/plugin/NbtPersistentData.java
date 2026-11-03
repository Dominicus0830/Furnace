// 플러그인 영속 데이터를 NBT compound 위에 구현한 것. 엔티티의 BukkitValues·청크의 ChunkBukkitValues 를 그대로 감싼다 —
// 모르는 꼴의 값(다른 서버 플러그인이 쓴 목록 등)은 건드리지 않고 보존한다. readOnly 면 쓰기를 거부한다(핸들 사본).
package org.domi.furnace.server.plugin;

import java.util.LinkedHashSet;
import java.util.Set;
import org.domi.furnace.api.persistence.PersistentDataContainer;
import org.domi.furnace.api.persistence.PersistentDataType;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

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
    public boolean has(String key) { return tag.tags().containsKey(PersistentDataType.checkKey(key)); }

    @Override
    public <T> T get(String key, PersistentDataType<T> type) {
        Object v = tag.tags().get(PersistentDataType.checkKey(key));
        if (v == null) return null;
        T out = convert(v, type);
        if (out == null) throw new IllegalArgumentException(key + " 의 값은 " + type + " 가 아니다");
        return out;
    }

    /** 저장된 NBT 값 → API 값. 종류가 맞지 않으면 null. 배열은 사본. */
    @SuppressWarnings("unchecked")
    private <T> T convert(Object v, PersistentDataType<T> type) {
        if (type == PersistentDataType.BOOLEAN) return v instanceof Byte b ? (T) Boolean.valueOf(b != 0) : null;
        if (type == PersistentDataType.TAG_CONTAINER) return v instanceof NbtCompound c ? (T) new NbtPersistentData(readOnly ? c.copy() : c, readOnly) : null;
        if (v instanceof byte[] a) return type == PersistentDataType.BYTE_ARRAY ? (T) a.clone() : null;
        if (v instanceof int[] a) return type == PersistentDataType.INTEGER_ARRAY ? (T) a.clone() : null;
        if (v instanceof long[] a) return type == PersistentDataType.LONG_ARRAY ? (T) a.clone() : null;
        return type.javaType().isInstance(v) ? (T) v : null;
    }

    @Override
    public Set<String> keys() { return new LinkedHashSet<>(tag.tags().keySet()); }

    @Override
    public boolean isEmpty() { return tag.tags().isEmpty(); }

    private void writable() {
        if (readOnly) throw new UnsupportedOperationException("읽기 전용 사본이다 — editPersistentData 안에서 고친다");
    }

    @Override
    public <T> void set(String key, PersistentDataType<T> type, T value) {
        writable();
        PersistentDataType.checkKey(key);
        if (value == null) throw new IllegalArgumentException("값이 null — 지우려면 remove");
        Object nbt;
        if (type == PersistentDataType.BOOLEAN) nbt = (byte) ((Boolean) value ? 1 : 0);
        else if (type == PersistentDataType.TAG_CONTAINER) {
            if (!(value instanceof NbtPersistentData c)) throw new IllegalArgumentException("중첩 컨테이너는 newContainer() 로 만든 것만");
            nbt = c.tag.copy();
        } else if (value instanceof byte[] a) nbt = a.clone();
        else if (value instanceof int[] a) nbt = a.clone();
        else if (value instanceof long[] a) nbt = a.clone();
        else if (type.javaType().isInstance(value)) nbt = value;
        else throw new IllegalArgumentException(value.getClass().getSimpleName() + " 는 " + type + " 값이 아니다");
        tag.tags().put(key, nbt);
    }

    @Override
    public void remove(String key) {
        writable();
        tag.tags().remove(PersistentDataType.checkKey(key));
    }

    @Override
    public PersistentDataContainer newContainer() { return new NbtPersistentData(new NbtCompound(), false); }
}
