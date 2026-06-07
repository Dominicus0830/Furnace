// 값 N개를 bitsPerEntry로 long 배열에 compacted 방식으로 팩킹(값이 long 경계를 넘지 않음)
package org.domi.furnace.server.world;

public final class BitStorage {

    private final int bitsPerEntry;
    private final int valuesPerLong;
    private final long mask;
    private final int size;
    private final long[] data;

    public BitStorage(int bitsPerEntry, int size) {
        if (bitsPerEntry < 1 || bitsPerEntry > 32) {
            throw new IllegalArgumentException("bitsPerEntry 범위 밖: " + bitsPerEntry);
        }
        this.bitsPerEntry = bitsPerEntry;
        this.valuesPerLong = 64 / bitsPerEntry;
        this.mask = (1L << bitsPerEntry) - 1;
        this.size = size;
        int longCount = (size + valuesPerLong - 1) / valuesPerLong;
        this.data = new long[longCount];
    }

    private BitStorage(int bitsPerEntry, int size, long[] data) {
        this.bitsPerEntry = bitsPerEntry;
        this.valuesPerLong = 64 / bitsPerEntry;
        this.mask = (1L << bitsPerEntry) - 1;
        this.size = size;
        this.data = data;
    }

    /** 기존 long[]를 감싼다(읽기용). */
    public static BitStorage wrap(int bitsPerEntry, int size, long[] data) {
        return new BitStorage(bitsPerEntry, size, data);
    }

    public void set(int index, int value) {
        int longIndex = index / valuesPerLong;
        int offset = (index % valuesPerLong) * bitsPerEntry;
        long v = data[longIndex];
        v &= ~(mask << offset);
        v |= ((long) value & mask) << offset;
        data[longIndex] = v;
    }

    public int get(int index) {
        int longIndex = index / valuesPerLong;
        int offset = (index % valuesPerLong) * bitsPerEntry;
        return (int) ((data[longIndex] >>> offset) & mask);
    }

    public long[] data() {
        return data;
    }

    public int size() {
        return size;
    }
}
