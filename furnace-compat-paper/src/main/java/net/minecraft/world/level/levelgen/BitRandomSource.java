package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/** 비트 단위 난수원(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 의 비트에서 값 만들기 그대로(java.util.Random 과 같은 식, nextDouble 의 float 상수 포함). */
public interface BitRandomSource extends RandomSource {

    float FLOAT_MULTIPLIER = 5.9604645E-8F;
    double DOUBLE_MULTIPLIER = 1.110223E-16F;

    int next(int bits);

    @Override
    default int nextInt() { return next(32); }

    @Override
    default int nextInt(int bound) {
        if (bound <= 0) throw new IllegalArgumentException("Bound must be positive");
        if ((bound & bound - 1) == 0) return (int) ((long) bound * (long) next(31) >> 31);
        int i;
        int j;
        do {
            i = next(31);
            j = i % bound;
        } while (i - j + (bound - 1) < 0);
        return j;
    }

    @Override
    default long nextLong() {
        int hi = next(32);
        int lo = next(32);
        return ((long) hi << 32) + (long) lo;
    }

    @Override
    default boolean nextBoolean() { return next(1) != 0; }

    @Override
    default float nextFloat() { return (float) next(24) * FLOAT_MULTIPLIER; }

    @Override
    default double nextDouble() {
        int hi = next(26);
        int lo = next(27);
        long l = ((long) hi << 27) + (long) lo;
        return (double) l * DOUBLE_MULTIPLIER;
    }
}
