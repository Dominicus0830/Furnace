package net.minecraft.nbt;

import java.util.Arrays;
import java.util.List;

/** byte 배열 태그(가변). 원소는 ByteTag 로 보인다. */
public final class ByteArrayTag extends CollectionTag<ByteTag> {

    private byte[] data;

    public ByteArrayTag(byte[] data) { this.data = data; }

    public ByteArrayTag(List<? extends Number> values) {
        this.data = new byte[values.size()];
        for (int i = 0; i < data.length; i++) data[i] = values.get(i).byteValue();
    }

    @Override
    public byte getId() { return TAG_BYTE_ARRAY; }

    @Override
    public byte getElementType() { return TAG_BYTE; }

    public byte[] getAsByteArray() { return data; }

    @Override
    public ByteArrayTag copy() { return new ByteArrayTag(Arrays.copyOf(data, data.length)); }

    @Override
    public int size() { return data.length; }

    @Override
    public ByteTag get(int i) { return ByteTag.valueOf(data[i]); }

    @Override
    public ByteTag set(int i, ByteTag tag) {
        byte old = data[i];
        data[i] = tag.getAsByte();
        return ByteTag.valueOf(old);
    }

    @Override
    public void add(int i, ByteTag tag) {
        byte[] n = Arrays.copyOf(data, data.length + 1);
        System.arraycopy(data, i, n, i + 1, data.length - i);
        n[i] = tag.getAsByte();
        data = n;
    }

    @Override
    public boolean setTag(int i, Tag tag) {
        if (tag instanceof NumericTag n) {
            data[i] = n.getAsByte();
            return true;
        }
        return false;
    }

    @Override
    public boolean addTag(int i, Tag tag) {
        if (tag instanceof NumericTag n) {
            add(i, ByteTag.valueOf(n.getAsByte()));
            return true;
        }
        return false;
    }

    @Override
    public ByteTag remove(int i) {
        byte old = data[i];
        byte[] n = new byte[data.length - 1];
        System.arraycopy(data, 0, n, 0, i);
        System.arraycopy(data, i + 1, n, i, data.length - i - 1);
        data = n;
        return ByteTag.valueOf(old);
    }

    @Override
    public void clear() { data = new byte[0]; }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof ByteArrayTag t && Arrays.equals(data, t.data); }

    @Override
    public int hashCode() { return Arrays.hashCode(data); }
}
