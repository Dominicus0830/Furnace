package net.minecraft.util;

import java.util.function.IntConsumer;

/**
 * 칸마다 bits 비트를 long 에 채우는 저장소(Mojang 이름 shim, N2b-1). 바닐라 1.21.1 과 같은 배치: long 하나에 64/bits 칸을 낮은 비트부터 채우고
 * 칸이 long 경계를 넘지 않는다. 값 검사(범위 밖 칸·mask 넘는 값 → IllegalArgumentException)와 길이 검사({@link InitializationException})도 같다.
 * 바닐라는 칸 → long 번호를 곱셈 마법 수로 나누지만 결과는 정수 나눗셈과 같아 그냥 나눈다.
 */
public class SimpleBitStorage implements BitStorage {

    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;
    private final int valuesPerLong;

    /** 칸 값 배열로 채운다(값은 mask 로 자른다 — 바닐라와 같다). */
    public SimpleBitStorage(int bits, int size, int[] values) {
        this(bits, size);
        int cell = 0;
        int i;
        for (i = 0; i <= size - valuesPerLong; i += valuesPerLong) {
            long v = 0L;
            for (int k = valuesPerLong - 1; k >= 0; k--) {
                v <<= bits;
                v |= (long) values[i + k] & mask;
            }
            data[cell++] = v;
        }
        int rest = size - i;
        if (rest > 0) {
            long v = 0L;
            for (int k = rest - 1; k >= 0; k--) {
                v <<= bits;
                v |= (long) values[i + k] & mask;
            }
            data[cell] = v;
        }
    }

    public SimpleBitStorage(int bits, int size) { this(bits, size, (long[]) null); }

    /** raw 가 있으면 그 배열을 그대로 쓴다(복사하지 않는다). 길이가 맞지 않으면 {@link InitializationException}. */
    public SimpleBitStorage(int bits, int size, long[] raw) {
        inclusiveBetween(1L, 32L, bits);
        this.size = size;
        this.bits = bits;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = (char) (64 / bits);
        int length = (size + valuesPerLong - 1) / valuesPerLong;
        if (raw != null) {
            if (raw.length != length) throw new InitializationException("Invalid length given for storage, got: " + raw.length + " but expected: " + length);
            this.data = raw;
        } else {
            this.data = new long[length];
        }
    }

    private int cellIndex(int index) { return index / valuesPerLong; }

    @Override
    public int getAndSet(int index, int value) {
        inclusiveBetween(0L, size - 1, index);
        inclusiveBetween(0L, mask, value);
        int cell = cellIndex(index);
        long v = data[cell];
        int shift = (index - cell * valuesPerLong) * bits;
        int old = (int) (v >> shift & mask);
        data[cell] = v & ~(mask << shift) | ((long) value & mask) << shift;
        return old;
    }

    @Override
    public void set(int index, int value) {
        inclusiveBetween(0L, size - 1, index);
        inclusiveBetween(0L, mask, value);
        int cell = cellIndex(index);
        long v = data[cell];
        int shift = (index - cell * valuesPerLong) * bits;
        data[cell] = v & ~(mask << shift) | ((long) value & mask) << shift;
    }

    @Override
    public int get(int index) {
        inclusiveBetween(0L, size - 1, index);
        int cell = cellIndex(index);
        int shift = (index - cell * valuesPerLong) * bits;
        return (int) (data[cell] >> shift & mask);
    }

    @Override
    public long[] getRaw() { return data; }

    @Override
    public int getSize() { return size; }

    @Override
    public int getBits() { return bits; }

    @Override
    public void getAll(IntConsumer consumer) {
        int n = 0;
        for (long v : data) {
            for (int k = 0; k < valuesPerLong; k++) {
                consumer.accept((int) (v & mask));
                v >>= bits;
                if (++n >= size) return;
            }
        }
    }

    @Override
    public void unpack(int[] out) {
        int length = data.length;
        int at = 0;
        for (int c = 0; c < length - 1; c++) {
            long v = data[c];
            for (int k = 0; k < valuesPerLong; k++) {
                out[at + k] = (int) (v & mask);
                v >>= bits;
            }
            at += valuesPerLong;
        }
        int rest = size - at;
        if (rest > 0) {
            long v = data[length - 1];
            for (int k = 0; k < rest; k++) {
                out[at + k] = (int) (v & mask);
                v >>= bits;
            }
        }
    }

    @Override
    public BitStorage copy() { return new SimpleBitStorage(bits, size, data.clone()); }

    /** commons-lang3 Validate.inclusiveBetween(long) 과 같은 검사·문구. */
    static void inclusiveBetween(long start, long end, long value) {
        if (value < start || value > end) {
            throw new IllegalArgumentException(String.format("The value %s is not in the specified inclusive range of %s to %s", value, start, end));
        }
    }

    public static class InitializationException extends RuntimeException {
        InitializationException(String message) { super(message); }
    }
}
