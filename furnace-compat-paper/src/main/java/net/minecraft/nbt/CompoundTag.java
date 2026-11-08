package net.minecraft.nbt;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 묶음 태그(가변, Mojang 이름 shim, H7b, 등급 A). 저장은 바닐라처럼 HashMap 이라 키 순서(getAllKeys)도 같다. getXxx 는 종류가 다르거나
 * 없으면 기본값(0·""·빈 배열·새 묶음/리스트 — 넣지는 않는다), contains(키, 99) 는 수 아무거나, UUID 는 int 4개 배열. merge 는 묶음끼리
 * 재귀로 합치고 나머지는 사본으로 덮는다(바닐라와 같다). Furnace 값({@link org.domi.furnace.api.nbt.NbtCompound})과는 {@link #fromFurnace}·
 * {@link #furnace} 로 오간다(깊은 사본).
 */
public class CompoundTag implements Tag {

    private final Map<String, Tag> tags;

    public CompoundTag() { this(new HashMap<>()); }

    protected CompoundTag(Map<String, Tag> tags) { this.tags = tags; }

    /** 호환 모듈 전용 — Furnace 값의 깊은 사본. */
    public static CompoundTag fromFurnace(org.domi.furnace.api.nbt.NbtCompound c) { return (CompoundTag) NbtBridge.fromFurnace(c); }

    /** 호환 모듈 전용 — 지금 값의 깊은 사본. */
    public org.domi.furnace.api.nbt.NbtCompound furnace() { return (org.domi.furnace.api.nbt.NbtCompound) NbtBridge.toFurnace(this); }

    /** 바닐라와 같이 살아 있는 키 집합. */
    public Set<String> getAllKeys() { return tags.keySet(); }

    @Override
    public byte getId() { return TAG_COMPOUND; }

    public int size() { return tags.size(); }

    public boolean isEmpty() { return tags.isEmpty(); }

    /** null 값은 바닐라와 같은 문구로 IllegalArgumentException. 이전 값(없으면 null). */
    public Tag put(String key, Tag tag) {
        if (tag == null) throw new IllegalArgumentException("Invalid null NBT value with key " + key);
        return tags.put(key, tag);
    }

    public void putByte(String key, byte v) { tags.put(key, ByteTag.valueOf(v)); }

    public void putShort(String key, short v) { tags.put(key, ShortTag.valueOf(v)); }

    public void putInt(String key, int v) { tags.put(key, IntTag.valueOf(v)); }

    public void putLong(String key, long v) { tags.put(key, LongTag.valueOf(v)); }

    public void putUUID(String key, UUID id) { tags.put(key, new IntArrayTag(uuidInts(id))); }

    public void putFloat(String key, float v) { tags.put(key, FloatTag.valueOf(v)); }

    public void putDouble(String key, double v) { tags.put(key, DoubleTag.valueOf(v)); }

    public void putString(String key, String v) { tags.put(key, StringTag.valueOf(v)); }

    public void putByteArray(String key, byte[] v) { tags.put(key, new ByteArrayTag(v)); }

    public void putByteArray(String key, java.util.List<Byte> v) { tags.put(key, new ByteArrayTag(v)); }

    public void putIntArray(String key, int[] v) { tags.put(key, new IntArrayTag(v)); }

    public void putIntArray(String key, java.util.List<Integer> v) { tags.put(key, new IntArrayTag(v)); }

    public void putLongArray(String key, long[] v) { tags.put(key, new LongArrayTag(v)); }

    public void putLongArray(String key, java.util.List<Long> v) { tags.put(key, new LongArrayTag(v)); }

    public void putBoolean(String key, boolean v) { tags.put(key, ByteTag.valueOf(v)); }

    public Tag get(String key) { return tags.get(key); }

    /** 없으면 0. */
    public byte getTagType(String key) {
        Tag t = tags.get(key);
        return t == null ? TAG_END : t.getId();
    }

    public boolean contains(String key) { return tags.containsKey(key); }

    /** type 99 = 수 태그 아무거나(1~6). */
    public boolean contains(String key, int type) {
        int t = getTagType(key);
        if (t == type) return true;
        if (type != TAG_ANY_NUMERIC) return false;
        return t >= TAG_BYTE && t <= TAG_DOUBLE;
    }

    public boolean hasUUID(String key) { return tags.get(key) instanceof IntArrayTag a && a.getAsIntArray().length == 4; }

    /** int 4개 배열이 아니면 바닐라처럼 IllegalArgumentException. */
    public UUID getUUID(String key) {
        Tag t = tags.get(key);
        if (!(t instanceof IntArrayTag a)) throw new IllegalArgumentException("Expected UUID-Tag to be of type " + IntArrayTag.class.getSimpleName() + ", but found " + (t == null ? "null" : t.getClass().getSimpleName()) + ".");
        int[] v = a.getAsIntArray();
        if (v.length != 4) throw new IllegalArgumentException("Expected UUID-Array to be of length 4, but found " + v.length + ".");
        return new UUID((long) v[0] << 32 | (long) v[1] & 4294967295L, (long) v[2] << 32 | (long) v[3] & 4294967295L);
    }

    private static int[] uuidInts(UUID id) {
        long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
        return new int[] {(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    private NumericTag num(String key) { return contains(key, TAG_ANY_NUMERIC) ? (NumericTag) tags.get(key) : null; }

    public byte getByte(String key) { NumericTag n = num(key); return n == null ? 0 : n.getAsByte(); }

    public short getShort(String key) { NumericTag n = num(key); return n == null ? 0 : n.getAsShort(); }

    public int getInt(String key) { NumericTag n = num(key); return n == null ? 0 : n.getAsInt(); }

    public long getLong(String key) { NumericTag n = num(key); return n == null ? 0L : n.getAsLong(); }

    public float getFloat(String key) { NumericTag n = num(key); return n == null ? 0.0F : n.getAsFloat(); }

    public double getDouble(String key) { NumericTag n = num(key); return n == null ? 0.0 : n.getAsDouble(); }

    public String getString(String key) { return contains(key, TAG_STRING) ? tags.get(key).getAsString() : ""; }

    public byte[] getByteArray(String key) { return contains(key, TAG_BYTE_ARRAY) ? ((ByteArrayTag) tags.get(key)).getAsByteArray() : new byte[0]; }

    public int[] getIntArray(String key) { return contains(key, TAG_INT_ARRAY) ? ((IntArrayTag) tags.get(key)).getAsIntArray() : new int[0]; }

    public long[] getLongArray(String key) { return contains(key, TAG_LONG_ARRAY) ? ((LongArrayTag) tags.get(key)).getAsLongArray() : new long[0]; }

    public CompoundTag getCompound(String key) { return contains(key, TAG_COMPOUND) ? (CompoundTag) tags.get(key) : new CompoundTag(); }

    /** 리스트가 아니거나, 비지 않았는데 원소 종류가 다르면 새 빈 리스트. */
    public ListTag getList(String key, int type) {
        if (getTagType(key) == TAG_LIST) {
            ListTag l = (ListTag) tags.get(key);
            if (!l.isEmpty() && l.getElementType() != type) return new ListTag();
            return l;
        }
        return new ListTag();
    }

    public boolean getBoolean(String key) { return getByte(key) != 0; }

    public void remove(String key) { tags.remove(key); }

    @Override
    public CompoundTag copy() {
        Map<String, Tag> c = new HashMap<>(tags.size() * 4 / 3 + 1);
        tags.forEach((k, v) -> c.put(k, v.copy()));
        return new CompoundTag(c);
    }

    /** 다른 묶음을 이 묶음에 합친다(묶음끼리는 재귀, 나머지는 사본으로 덮기). 자기 자신을 돌려준다. */
    public CompoundTag merge(CompoundTag other) {
        for (String k : other.tags.keySet()) {
            Tag t = other.tags.get(k);
            if (t.getId() == TAG_COMPOUND) {
                if (contains(k, TAG_COMPOUND)) {
                    getCompound(k).merge((CompoundTag) t);
                } else {
                    put(k, t.copy());
                }
            } else {
                put(k, t.copy());
            }
        }
        return this;
    }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof CompoundTag c && tags.equals(c.tags); }

    @Override
    public int hashCode() { return tags.hashCode(); }

    @Override
    public String toString() { return org.domi.furnace.api.nbt.Snbt.write(NbtBridge.toFurnace(this)); }
}
