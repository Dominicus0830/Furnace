package net.minecraft.nbt;

import java.util.Arrays;
import java.util.List;

/** long 배열 태그(가변). 원소는 LongTag 로 보인다. */
public final class LongArrayTag extends CollectionTag<LongTag> {

    private long[] data;

    public LongArrayTag(long[] data) { this.data = data; }

    public LongArrayTag(List<? extends Number> values) {
        this.data = new long[values.size()];
        for (int i = 0; i < data.length; i++) data[i] = values.get(i).longValue();
    }

    @Override
    public byte getId() { return TAG_LONG_ARRAY; }

    @Override
    public byte getElementType() { return TAG_LONG; }

    public long[] getAsLongArray() { return data; }

    @Override
    public LongArrayTag copy() { return new LongArrayTag(Arrays.copyOf(data, data.length)); }

    @Override
    public int size() { return data.length; }

    @Override
    public LongTag get(int i) { return LongTag.valueOf(data[i]); }

    @Override
    public LongTag set(int i, LongTag tag) {
        long old = data[i];
        data[i] = tag.getAsLong();
        return LongTag.valueOf(old);
    }

    @Override
    public void add(int i, LongTag tag) {
        long[] n = Arrays.copyOf(data, data.length + 1);
        System.arraycopy(data, i, n, i + 1, data.length - i);
        n[i] = tag.getAsLong();
        data = n;
    }

    @Override
    public boolean setTag(int i, Tag tag) {
        if (tag instanceof NumericTag n) {
            data[i] = n.getAsLong();
            return true;
        }
        return false;
    }

    @Override
    public boolean addTag(int i, Tag tag) {
        if (tag instanceof NumericTag n) {
            add(i, LongTag.valueOf(n.getAsLong()));
            return true;
        }
        return false;
    }

    @Override
    public LongTag remove(int i) {
        long old = data[i];
        long[] n = new long[data.length - 1];
        System.arraycopy(data, 0, n, 0, i);
        System.arraycopy(data, i + 1, n, i, data.length - i - 1);
        data = n;
        return LongTag.valueOf(old);
    }

    @Override
    public void clear() { data = new long[0]; }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof LongArrayTag t && Arrays.equals(data, t.data); }

    @Override
    public int hashCode() { return Arrays.hashCode(data); }
}
