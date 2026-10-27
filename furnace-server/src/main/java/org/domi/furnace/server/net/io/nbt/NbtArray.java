// 바꿀 수 있는 숫자 배열 태그(바닐라 ByteArrayTag·IntArrayTag·LongArrayTag 의 CollectionTag 동작).
// 평소 NBT 값은 byte[]/int[]/long[] 이지만 NBT 경로 연산은 배열에 끼우고 빼야 하므로, 연산 동안만 이 꼴로 바꿔 둔다(NbtTags.thaw/freeze).
package org.domi.furnace.server.net.io.nbt;

import java.util.ArrayList;
import java.util.List;

public final class NbtArray {

    private final byte type;
    private final List<Long> data;

    public NbtArray(byte type, List<Long> data) {
        this.type = type;
        this.data = data;
    }

    public static NbtArray of(Object array) {
        List<Long> l = new ArrayList<>();
        byte t;
        switch (array) {
            case byte[] a -> { t = NbtIo.TAG_BYTE_ARRAY; for (byte b : a) l.add((long) b); }
            case int[] a -> { t = NbtIo.TAG_INT_ARRAY; for (int b : a) l.add((long) b); }
            case long[] a -> { t = NbtIo.TAG_LONG_ARRAY; for (long b : a) l.add(b); }
            default -> throw new IllegalArgumentException("배열이 아님: " + array);
        }
        return new NbtArray(t, l);
    }

    public byte type() { return type; }

    public int size() { return data.size(); }

    /** 원소 태그: 바이트 배열은 Byte, 정수 배열은 Integer, long 배열은 Long. */
    public Object get(int i) {
        long v = data.get(i);
        return switch (type) {
            case NbtIo.TAG_BYTE_ARRAY -> (byte) v;
            case NbtIo.TAG_INT_ARRAY -> (int) v;
            default -> v;
        };
    }

    public List<Object> elements() {
        List<Object> out = new ArrayList<>(data.size());
        for (int i = 0; i < data.size(); i++) out.add(get(i));
        return out;
    }

    /** 숫자 태그를 이 배열 원소 꼴로(NumericTag.getAsByte/getAsInt/getAsLong). */
    private long convert(Number n) {
        return switch (type) {
            case NbtIo.TAG_BYTE_ARRAY -> NbtTags.asByte(n);
            case NbtIo.TAG_INT_ARRAY -> NbtTags.asInt(n);
            default -> NbtTags.asLong(n);
        };
    }

    /** addTag: 숫자 태그만 받는다. */
    public boolean addTag(int i, Object v) {
        if (!(v instanceof Number n)) return false;
        data.add(i, convert(n));
        return true;
    }

    /** setTag: 숫자 태그만 받는다. */
    public boolean setTag(int i, Object v) {
        if (!(v instanceof Number n)) return false;
        data.set(i, convert(n));
        return true;
    }

    public void remove(int i) { data.remove(i); }

    public void clear() { data.clear(); }

    public NbtArray copy() { return new NbtArray(type, new ArrayList<>(data)); }

    /** 원래 꼴(byte[]/int[]/long[]). */
    public Object toArray() {
        int n = data.size();
        switch (type) {
            case NbtIo.TAG_BYTE_ARRAY -> { byte[] a = new byte[n]; for (int i = 0; i < n; i++) a[i] = (byte) (long) data.get(i); return a; }
            case NbtIo.TAG_INT_ARRAY -> { int[] a = new int[n]; for (int i = 0; i < n; i++) a[i] = (int) (long) data.get(i); return a; }
            default -> { long[] a = new long[n]; for (int i = 0; i < n; i++) a[i] = data.get(i); return a; }
        }
    }

    @Override
    public boolean equals(Object o) { return o instanceof NbtArray a && a.type == type && a.data.equals(data); }

    @Override
    public int hashCode() { return type * 31 + data.hashCode(); }
}
