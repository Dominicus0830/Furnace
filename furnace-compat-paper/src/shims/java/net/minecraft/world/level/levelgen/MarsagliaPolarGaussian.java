package net.minecraft.world.level.levelgen;

import net.minecraft.util.RandomSource;

/** 정규 분포 값(Mojang 이름 shim, N2b-6) — 바닐라 1.21.1 의 마르살리아 극좌표법 그대로(java.util.Random#nextGaussian 과 같은 식). */
class MarsagliaPolarGaussian {

    public final RandomSource randomSource;
    private double nextNextGaussian;
    private boolean haveNextNextGaussian;

    public MarsagliaPolarGaussian(RandomSource randomSource) { this.randomSource = randomSource; }

    public void reset() { haveNextNextGaussian = false; }

    public double nextGaussian() {
        if (haveNextNextGaussian) {
            haveNextNextGaussian = false;
            return nextNextGaussian;
        }
        double v1;
        double v2;
        double s;
        do {
            v1 = 2.0 * randomSource.nextDouble() - 1.0;
            v2 = 2.0 * randomSource.nextDouble() - 1.0;
            s = v1 * v1 + v2 * v2;
        } while (s >= 1.0 || s == 0.0);
        double m = Math.sqrt(-2.0 * Math.log(s) / s);
        nextNextGaussian = v2 * m;
        haveNextNextGaussian = true;
        return v1 * m;
    }
}
