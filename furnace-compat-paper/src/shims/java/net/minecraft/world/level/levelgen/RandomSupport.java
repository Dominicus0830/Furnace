package net.minecraft.world.level.levelgen;

import java.util.concurrent.atomic.AtomicLong;

/** 씨앗 도우미(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 의 겹치지 않는 씨앗 만들기 그대로. */
class RandomSupport {

    private static final AtomicLong SEED_UNIQUIFIER = new AtomicLong(8682522807148012L);

    private RandomSupport() {}

    public static long mixStafford13(long seed) {
        seed = (seed ^ seed >>> 30) * -4658895280553007687L;
        seed = (seed ^ seed >>> 27) * -7723592293110705685L;
        return seed ^ seed >>> 31;
    }

    public static long generateUniqueSeed() { return SEED_UNIQUIFIER.updateAndGet(v -> v * 1181783497276652981L) ^ System.nanoTime(); }
}
