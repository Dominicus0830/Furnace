package net.minecraft.util;

import java.util.Arrays;
import java.util.function.IntConsumer;

/** 모든 칸이 0 인 저장소(Mojang 이름 shim, N2b-1) — 값 하나뿐인 팔레트용. 바닐라 1.21.1 포팅: 0 아닌 값·범위 밖 칸은 IllegalArgumentException. */
public class ZeroBitStorage implements BitStorage {

    public static final long[] RAW = new long[0];

    private final int size;

    public ZeroBitStorage(int size) { this.size = size; }

    @Override
    public int getAndSet(int index, int value) {
        SimpleBitStorage.inclusiveBetween(0L, size - 1, index);
        SimpleBitStorage.inclusiveBetween(0L, 0L, value);
        return 0;
    }

    @Override
    public void set(int index, int value) {
        SimpleBitStorage.inclusiveBetween(0L, size - 1, index);
        SimpleBitStorage.inclusiveBetween(0L, 0L, value);
    }

    @Override
    public int get(int index) {
        SimpleBitStorage.inclusiveBetween(0L, size - 1, index);
        return 0;
    }

    @Override
    public long[] getRaw() { return RAW; }

    @Override
    public int getSize() { return size; }

    @Override
    public int getBits() { return 0; }

    @Override
    public void getAll(IntConsumer consumer) {
        for (int i = 0; i < size; i++) consumer.accept(0);
    }

    @Override
    public void unpack(int[] out) { Arrays.fill(out, 0, size, 0); }

    /** 바닐라: 바꿀 것이 없어 자기 자신. */
    @Override
    public BitStorage copy() { return this; }
}
