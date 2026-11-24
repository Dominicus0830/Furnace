package net.minecraft.world.level.levelgen;

/** Xoroshiro128++ 난수 생성기(Mojang 이름 shim) — 바닐라 1.21.1 의 갱신식 그대로(회전 17·49·28, 이동 21). 코덱은 껍데기(T-261)다. */
class Xoroshiro128PlusPlus {

    private long lo;
    private long hi;

    public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) { this(seed.seedLo(), seed.seedHi()); }

    public Xoroshiro128PlusPlus(long lo, long hi) {
        this.lo = lo;
        this.hi = hi;
        // 바닐라: 두 값이 모두 0 이면 황금비·은비 상수로 바꾼다(전부 0 은 이 생성기가 못 쓰는 상태)
        if ((this.lo | this.hi) == 0L) {
            this.lo = -7046029254386353131L;
            this.hi = 7640891576956012809L;
        }
    }

    public long nextLong() {
        long l = this.lo;
        long m = this.hi;
        long n = Long.rotateLeft(l + m, 17) + l;
        m ^= l;
        this.lo = Long.rotateLeft(l, 49) ^ m ^ m << 21;
        this.hi = Long.rotateLeft(m, 28);
        return n;
    }
}
