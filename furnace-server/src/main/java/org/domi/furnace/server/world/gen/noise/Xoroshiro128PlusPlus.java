// Xoroshiro128++ 코어(바닐라 net.minecraft.world.level.levelgen.Xoroshiro128PlusPlus)
package org.domi.furnace.server.world.gen.noise;

public final class Xoroshiro128PlusPlus {
    private long seedLo, seedHi;
    public Xoroshiro128PlusPlus(long seedLo, long seedHi) {
        this.seedLo = seedLo; this.seedHi = seedHi;
        if ((this.seedLo | this.seedHi) == 0L) { this.seedLo = -7046029254386353131L; this.seedHi = 7640891576956012809L; }
    }
    public Xoroshiro128PlusPlus(RandomSupport.Seed128bit seed) {
        this(seed.seedLo(), seed.seedHi());
    }
    public long nextLong() {
        long l = this.seedLo, l1 = this.seedHi;
        long l2 = Long.rotateLeft(l + l1, 17) + l;
        l1 ^= l;
        this.seedLo = Long.rotateLeft(l, 49) ^ l1 ^ l1 << 21;
        this.seedHi = Long.rotateLeft(l1, 28);
        return l2;
    }
}
