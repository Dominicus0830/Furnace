// 시드 128비트 확장 + Stafford13 믹스(바닐라 RandomSupport)
package org.domi.furnace.server.world.gen.noise;

public final class RandomSupport {
    private RandomSupport() {}
    public static long mixStafford13(long s) {
        s = (s ^ s >>> 30) * -4658895280553007687L;
        s = (s ^ s >>> 27) * -7723592293110705685L;
        return s ^ s >>> 31;
    }
    public record Seed128bit(long seedLo, long seedHi) {
        public Seed128bit xor(long lo, long hi) { return new Seed128bit(seedLo ^ lo, seedHi ^ hi); }
        public Seed128bit mixed() { return new Seed128bit(mixStafford13(seedLo), mixStafford13(seedHi)); }
    }
    public static Seed128bit upgradeSeedTo128bitUnmixed(long seed) {
        long l = seed ^ 7640891576956012809L; long l1 = l + -7046029254386353131L;
        return new Seed128bit(l, l1);
    }
    public static Seed128bit upgradeSeedTo128bit(long seed) { return upgradeSeedTo128bitUnmixed(seed).mixed(); }
    public static Seed128bit seedFromHashOf(String s) {
        // 바닐라: MD5 128-bit of UTF-8 string → (first 8 bytes big-endian = lo, next 8 = hi)
        try {
            byte[] h = java.security.MessageDigest.getInstance("MD5").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            long lo = 0, hi = 0;
            for (int i = 0; i < 8; i++) lo = (lo << 8) | (h[i] & 0xFFL);
            for (int i = 8; i < 16; i++) hi = (hi << 8) | (h[i] & 0xFFL);
            return new Seed128bit(lo, hi);
        } catch (java.security.NoSuchAlgorithmException e) { throw new RuntimeException(e); }
    }
}
