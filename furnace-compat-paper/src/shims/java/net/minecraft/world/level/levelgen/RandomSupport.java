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

    /** 바닐라 Seed128bit(레코드): 128비트 씨앗 두 절반. */
    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long lo, long hi) { return new Seed128bit(this.seedLo ^ lo, this.seedHi ^ hi); }

        public Seed128bit xor(Seed128bit other) { return this.xor(other.seedLo, other.seedHi); }

        public Seed128bit mixed() { return new Seed128bit(mixStafford13(this.seedLo), mixStafford13(this.seedHi)); }
    }

    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long l = seed ^ 7640891576956012809L;
        long m = l + -7046029254386353131L;
        return new Seed128bit(l, m);
    }

    public static Seed128bit upgradeSeedTo128bit(long seed) { return upgradeSeedTo128bitUnmixed(seed).mixed(); }

    /** 바닐라: 이름의 UTF-8 MD5(16바이트)를 큰 쪽 먼저 읽은 long 두 개. */
    public static Seed128bit seedFromHashOf(String seed) {
        byte[] bs;
        try {
            bs = java.security.MessageDigest.getInstance("MD5").digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bs);
        long l = buf.getLong();
        long m = buf.getLong();
        return new Seed128bit(l, m);
    }
}
