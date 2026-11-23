package net.minecraft.nbt;

import java.util.Arrays;
import java.util.List;

/** int 배열 태그(가변). 원소는 IntTag 로 보인다. */
public final class IntArrayTag extends CollectionTag<IntTag> {

    /** 바닐라 {@code IntArrayTag.TYPE}: 이 클래스 안의 익명 종류(이름·표시 이름·isValue 는 바닐라 1.21.1 jar 와 대조). */
    public static final TagType<IntArrayTag> TYPE = new TagType<>() {
        @Override public String getName() { return "INT[]"; }
        @Override public String getPrettyName() { return "TAG_Int_Array"; }
    };

    private int[] data;

    public IntArrayTag(int[] data) { this.data = data; }

    public IntArrayTag(List<? extends Number> values) {
        this.data = new int[values.size()];
        for (int i = 0; i < data.length; i++) data[i] = values.get(i).intValue();
    }

    @Override
    public byte getId() { return TAG_INT_ARRAY; }

    /** 바닐라: 메모리 크기 어림(NbtAccounter 가 쓰는 값과 같은 셈). */
    @Override
    public int sizeInBytes() {
        return 24 + 4 * data.length;
    }

    @Override
    public byte getElementType() { return TAG_INT; }

    public int[] getAsIntArray() { return data; }

    @Override
    public IntArrayTag copy() { return new IntArrayTag(Arrays.copyOf(data, data.length)); }

    @Override
    public int size() { return data.length; }

    @Override
    public IntTag get(int i) { return IntTag.valueOf(data[i]); }

    @Override
    public IntTag set(int i, IntTag tag) {
        int old = data[i];
        data[i] = tag.getAsInt();
        return IntTag.valueOf(old);
    }

    @Override
    public void add(int i, IntTag tag) {
        // 바닐라(ArrayUtils.add)처럼 범위 밖은 IndexOutOfBoundsException(배열 판이 아니라) — T-235 차분 시험
        if (i < 0 || i > data.length) throw new IndexOutOfBoundsException("Index: " + i + ", Length: " + data.length);
        int[] n = Arrays.copyOf(data, data.length + 1);
        System.arraycopy(data, i, n, i + 1, data.length - i);
        n[i] = tag.getAsInt();
        data = n;
    }

    @Override
    public boolean setTag(int i, Tag tag) {
        if (tag instanceof NumericTag n) {
            data[i] = n.getAsInt();
            return true;
        }
        return false;
    }

    @Override
    public boolean addTag(int i, Tag tag) {
        if (tag instanceof NumericTag n) {
            add(i, IntTag.valueOf(n.getAsInt()));
            return true;
        }
        return false;
    }

    @Override
    public IntTag remove(int i) {
        int old = data[i];
        int[] n = new int[data.length - 1];
        System.arraycopy(data, 0, n, 0, i);
        System.arraycopy(data, i + 1, n, i, data.length - i - 1);
        data = n;
        return IntTag.valueOf(old);
    }

    @Override
    public void clear() { data = new int[0]; }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof IntArrayTag t && Arrays.equals(data, t.data); }

    @Override
    public int hashCode() { return Arrays.hashCode(data); }
}
