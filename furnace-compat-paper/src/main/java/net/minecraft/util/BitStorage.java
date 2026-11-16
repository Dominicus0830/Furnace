package net.minecraft.util;

import java.util.function.IntConsumer;

/** 고정 비트 칸 저장소(Mojang 이름 shim, N2b-1). 바닐라 1.21.1 과 같은 인터페이스 — 구현은 {@link SimpleBitStorage}·{@link ZeroBitStorage}. */
public interface BitStorage {

    int getAndSet(int index, int value);

    void set(int index, int value);

    int get(int index);

    /** 바탕 long 배열 그 자체(사본 아님 — 바닐라와 같다). */
    long[] getRaw();

    int getSize();

    int getBits();

    void getAll(IntConsumer consumer);

    void unpack(int[] out);

    BitStorage copy();
}
