package net.minecraft.util;

/** 바닐라 LinearCongruentialGenerator(Mojang 이름 shim, T-211 3단계) — 바이옴 확대 지터가 쓰는 선형 합동 단계. */
public class LinearCongruentialGenerator {

    private static final long MULTIPLIER = 6364136223846793005L;
    private static final long INCREMENT = 1442695040888963407L;

    public static long next(long seed, long salt) {
        seed *= seed * MULTIPLIER + INCREMENT;
        return seed + salt;
    }
}
