package net.minecraft.nbt;

import java.util.Arrays;
import java.util.List;

/** byte 배열 태그(가변). 원소는 ByteTag 로 보인다. */
public final class ByteArrayTag extends CollectionTag<ByteTag> {

    /** 바닐라 {@code ByteArrayTag.TYPE}: 이 클래스 안의 익명 종류(이름·표시 이름·isValue 는 바닐라 1.21.1 jar 와 대조). */
    public static final TagType<ByteArrayTag> TYPE = new TagType<>() {
        @Override public String getName() { return "BYTE[]"; }
        @Override public String getPrettyName() { return "TAG_Byte_Array"; }
    };

    private byte[] data;

    public ByteArrayTag(byte[] data) { this.data = data; }

    public ByteArrayTag(List<? extends Number> values) {
        this.data = new byte[values.size()];
        for (int i = 0; i < data.length; i++) data[i] = values.get(i).byteValue();
    }

    @Override
    public byte getId() { return TAG_BYTE_ARRAY; }

    /** 바닐라: 메모리 크기 어림(NbtAccounter 가 쓰는 값과 같은 셈). */
    @Override
    public int sizeInBytes() {
        return 24 + 1 * data.length;
    }

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
        // 바닐라(ArrayUtils.add)처럼 범위 밖은 IndexOutOfBoundsException(배열 판이 아니라) — T-235 차분 시험
        if (i < 0 || i > data.length) throw new IndexOutOfBoundsException("Index: " + i + ", Length: " + data.length);
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
