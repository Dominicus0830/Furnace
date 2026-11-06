// 이름 붙은 NBT 태그 묶음(바닐라 CompoundTag 와 같은 규칙). 바꿀 수 있고 스레드 안전하지 않다 — 한 스레드에서 만들고 고친다.
// 게터는 바닐라처럼 키가 없거나 형이 맞지 않으면 기본값(0·false·""·빈 배열·빈 묶음)을 돌려준다. 숫자 태그끼리는 변환한다.
// SNBT 로 만들기: NbtCompound.parse("{NoAI:1b,Tags:[\"boss\"]}"), toString() 은 같은 꼴의 한 줄 SNBT(키 정렬).
// 이 타입은 서버 버전과 무관하다. 다만 엔티티·블록 엔티티 NBT 의 "키 이름과 구조"는 마인크래프트 데이터 버전을 따른다(Furnace.server().minecraftVersion()).
package org.domi.furnace.api.nbt;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class NbtCompound {

    private final Map<String, Object> tags = new LinkedHashMap<>();

    public NbtCompound() {}

    /** SNBT 한 개를 읽는다. 묶음이 아니거나 문법이 틀리면 {@link SnbtParseException}. */
    public static NbtCompound parse(String snbt) {
        Object v = Snbt.parse(snbt);
        if (!(v instanceof NbtCompound c)) throw new SnbtParseException("묶음({...})이 아니다: " + snbt, 0);
        return c;
    }

    // ------------------------------------------------------------------ 기본

    public int size() { return tags.size(); }

    public boolean isEmpty() { return tags.isEmpty(); }

    /** 키 목록(넣은 순서, 바꿀 수 없는 보기). */
    public Set<String> keys() { return Collections.unmodifiableSet(tags.keySet()); }

    public boolean contains(String key) { return tags.containsKey(key); }

    /** 키가 있고 그 종류인가. type 이 숫자 종류(BYTE..DOUBLE)면 숫자 태그 아무거나 맞는다(바닐라 contains(k, 99) 와 비슷). */
    public boolean contains(String key, NbtType type) {
        Object v = tags.get(key);
        if (v == null) return false;
        NbtType t = NbtType.of(v);
        return t == type || type.isNumber() && t.isNumber();
    }

    /** 키의 종류, 없으면 null. */
    public NbtType type(String key) {
        Object v = tags.get(key);
        return v == null ? null : NbtType.of(v);
    }

    /** 날 값(Byte·Short·…·NbtList·NbtCompound), 없으면 null. 배열·묶음·리스트는 살아 있는 값이다. */
    public Object get(String key) { return tags.get(key); }

    /** 날 값을 넣는다. NBT 값이 아니면 IllegalArgumentException. 자기 자신을 돌려준다. */
    public NbtCompound put(String key, Object value) {
        Objects.requireNonNull(key, "key");
        NbtType.of(value);
        if (value == this) throw new IllegalArgumentException("자기 자신을 넣을 수 없다");
        tags.put(key, value);
        return this;
    }

    /** 지운 값(없으면 null). */
    public Object remove(String key) { return tags.remove(key); }

    public void clear() { tags.clear(); }

    // ------------------------------------------------------------------ 넣기

    public NbtCompound putByte(String key, byte v) { return put(key, v); }

    public NbtCompound putShort(String key, short v) { return put(key, v); }

    public NbtCompound putInt(String key, int v) { return put(key, v); }

    public NbtCompound putLong(String key, long v) { return put(key, v); }

    public NbtCompound putFloat(String key, float v) { return put(key, v); }

    public NbtCompound putDouble(String key, double v) { return put(key, v); }

    /** 바이트 1/0 으로 넣는다(바닐라 putBoolean). */
    public NbtCompound putBoolean(String key, boolean v) { return put(key, (byte) (v ? 1 : 0)); }

    public NbtCompound putString(String key, String v) { return put(key, Objects.requireNonNull(v, "value")); }

    public NbtCompound putByteArray(String key, byte[] v) { return put(key, v); }

    public NbtCompound putIntArray(String key, int[] v) { return put(key, v); }

    public NbtCompound putLongArray(String key, long[] v) { return put(key, v); }

    public NbtCompound putCompound(String key, NbtCompound v) { return put(key, v); }

    public NbtCompound putList(String key, NbtList v) { return put(key, v); }

    /** UUID 를 int[4] 로(바닐라 NbtUtils.createUUID, 엔티티 UUID·Owner 등의 꼴). */
    public NbtCompound putUuid(String key, UUID id) {
        long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
        return put(key, new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l});
    }

    // ------------------------------------------------------------------ 읽기(바닐라 CompoundTag 게터 규칙)

    private static int floorInt(double d) {
        int i = (int) d;
        return d < i ? i - 1 : i;
    }

    public byte getByte(String key) {
        Object o = tags.get(key);
        if (o instanceof Float || o instanceof Double) return (byte) floorInt(((Number) o).doubleValue());
        return o instanceof Number n ? n.byteValue() : 0;
    }

    public short getShort(String key) {
        Object o = tags.get(key);
        if (o instanceof Float || o instanceof Double) return (short) floorInt(((Number) o).doubleValue());
        return o instanceof Number n ? n.shortValue() : 0;
    }

    public int getInt(String key) {
        Object o = tags.get(key);
        if (o instanceof Float || o instanceof Double) return floorInt(((Number) o).doubleValue());
        return o instanceof Number n ? n.intValue() : 0;
    }

    public long getLong(String key) {
        Object o = tags.get(key);
        if (o instanceof Double d) {
            long l = (long) (double) d;
            return d < l ? l - 1 : l;
        }
        return o instanceof Number n ? n.longValue() : 0L;
    }

    public float getFloat(String key) { return tags.get(key) instanceof Number n ? n.floatValue() : 0f; }

    public double getDouble(String key) { return tags.get(key) instanceof Number n ? n.doubleValue() : 0.0; }

    public boolean getBoolean(String key) { return getByte(key) != 0; }

    public String getString(String key) { return tags.get(key) instanceof String s ? s : ""; }

    /** 살아 있는 배열(고치면 이 묶음이 바뀐다). 없으면 새 빈 배열. */
    public byte[] getByteArray(String key) { return tags.get(key) instanceof byte[] a ? a : new byte[0]; }

    public int[] getIntArray(String key) { return tags.get(key) instanceof int[] a ? a : new int[0]; }

    public long[] getLongArray(String key) { return tags.get(key) instanceof long[] a ? a : new long[0]; }

    /** 살아 있는 하위 묶음. 없거나 묶음이 아니면 이 묶음에 붙지 않은 새 빈 묶음(바닐라 getCompound). 고쳐서 넣으려면 {@link #compound(String)}. */
    public NbtCompound getCompound(String key) { return tags.get(key) instanceof NbtCompound c ? c : new NbtCompound(); }

    /** 살아 있는 리스트. 없거나 리스트가 아니면 붙지 않은 새 빈 리스트. 고쳐서 넣으려면 {@link #list(String)}. */
    public NbtList getList(String key) { return tags.get(key) instanceof NbtList l ? l : new NbtList(); }

    /** 하위 묶음을 돌려주되, 없거나 묶음이 아니면 새로 만들어 이 키에 넣는다 — 깊은 경로를 고칠 때: {@code nbt.compound("Brain").compound("memories")}. */
    public NbtCompound compound(String key) {
        if (tags.get(key) instanceof NbtCompound c) return c;
        NbtCompound c = new NbtCompound();
        tags.put(key, c);
        return c;
    }

    /** 리스트를 돌려주되, 없거나 리스트가 아니면 새로 만들어 이 키에 넣는다. */
    public NbtList list(String key) {
        if (tags.get(key) instanceof NbtList l) return l;
        NbtList l = new NbtList();
        tags.put(key, l);
        return l;
    }

    /** int[4] UUID(바닐라 NbtUtils.loadUUID), 꼴이 아니면 null. */
    public UUID getUuid(String key) {
        if (!(tags.get(key) instanceof int[] a) || a.length != 4) return null;
        return new UUID((long) a[0] << 32 | a[1] & 0xFFFFFFFFL, (long) a[2] << 32 | a[3] & 0xFFFFFFFFL);
    }

    // ------------------------------------------------------------------ 합치기·복사

    /**
     * other 를 이 묶음에 합친다(바닐라 CompoundTag.merge, /data merge 와 같다): 양쪽 다 묶음인 키는 안쪽으로 합치고,
     * 나머지 키는 other 의 값(복사본)으로 덮는다. 리스트는 통째로 바뀐다. 자기 자신을 돌려준다.
     */
    public NbtCompound merge(NbtCompound other) {
        for (Map.Entry<String, Object> e : other.tags.entrySet()) {
            Object v = e.getValue();
            if (v instanceof NbtCompound oc && tags.get(e.getKey()) instanceof NbtCompound mine) mine.merge(oc);
            else tags.put(e.getKey(), NbtType.copy(v));
        }
        return this;
    }

    /** 깊은 복사. */
    public NbtCompound copy() {
        NbtCompound c = new NbtCompound();
        for (Map.Entry<String, Object> e : tags.entrySet()) c.tags.put(e.getKey(), NbtType.copy(e.getValue()));
        return c;
    }

    /** 서버 변환용: 넣은 순서의 항목들(바꿀 수 없는 보기). */
    public Set<Map.Entry<String, Object>> entries() { return Collections.unmodifiableMap(tags).entrySet(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NbtCompound c) || c.tags.size() != tags.size()) return false;
        for (Map.Entry<String, Object> e : tags.entrySet()) {
            Object w = c.tags.get(e.getKey());
            if (w == null || !NbtType.equal(e.getValue(), w)) return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (Map.Entry<String, Object> e : tags.entrySet()) h += e.getKey().hashCode() ^ NbtType.hash(e.getValue());
        return h;
    }

    /** 한 줄 SNBT(키 정렬) — {@link #parse} 로 다시 읽힌다. */
    @Override
    public String toString() { return Snbt.write(this); }
}
