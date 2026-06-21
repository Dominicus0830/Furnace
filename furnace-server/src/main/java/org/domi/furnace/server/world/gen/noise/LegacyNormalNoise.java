// 바닐라 LegacyRandomSource 경로 NormalNoise 비트정확 포팅 — state provider noise(flower 등) 전용
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.carver.LegacyRandom;

/**
 * 바닐라 net.minecraft.world.level.levelgen.synth.NormalNoise 의 LegacyRandomSource 경로 포팅.
 *
 * <p>state provider(noise_threshold_provider 등)는 {@code NormalNoise.create(new WorldgenRandom(
 * new LegacyRandomSource(seed)), params)} 로 noise 를 만든다. PerlinNoise.create(useNewFactory=true)는
 * {@code random.forkPositional()} 로 factory seed(=nextLong 1회)를 만들고, 각 옥타브를
 * {@code fromHashOf("octave_"+N)} = {@code new LegacyRandomSource(("octave_"+N).hashCode() ^ factorySeed)} 로
 * 시드한다 (LegacyRandomSource.java:71-74). Xoroshiro 경로(MD5 기반)와 시드가 완전히 달라 Furnace 기존
 * NormalNoise 는 재사용 불가. noise 산술은 검증된 {@link ImprovedNoise} 를 재사용한다.
 */
public final class LegacyNormalNoise {

    private static final double INPUT_FACTOR = 1.0181268882175227;

    private final double valueFactor;
    private final LegacyPerlin first;
    private final LegacyPerlin second;

    public LegacyNormalNoise(long seed, int firstOctave, double[] amplitudes) {
        // new WorldgenRandom(new LegacyRandomSource(seed)) 와 동일 LCG.
        LegacyRandom random = new LegacyRandom(seed);
        this.first = new LegacyPerlin(random, firstOctave, amplitudes);
        this.second = new LegacyPerlin(random, firstOctave, amplitudes);

        int i1 = Integer.MAX_VALUE;
        int i2 = Integer.MIN_VALUE;
        for (int i = 0; i < amplitudes.length; i++) {
            if (amplitudes[i] != 0.0) {
                i1 = Math.min(i1, i);
                i2 = Math.max(i2, i);
            }
        }
        this.valueFactor = 0.16666666666666666 / expectedDeviation(i2 - i1);
    }

    private static double expectedDeviation(int octaves) {
        return 0.1 * (1.0 + 1.0 / (octaves + 1));
    }

    public double getValue(double x, double y, double z) {
        double d  = x * INPUT_FACTOR;
        double d1 = y * INPUT_FACTOR;
        double d2 = z * INPUT_FACTOR;
        return (this.first.getValue(x, y, z) + this.second.getValue(d, d1, d2)) * this.valueFactor;
    }

    /**
     * 바닐라 PerlinNoise(useNewFactory=true) 의 LegacyRandomSource 경로.
     * forkPositional()=LegacyPositionalRandomFactory(random.nextLong()),
     * 각 옥타브 = new ImprovedNoise(new LegacyRandom(("octave_"+N).hashCode() ^ factorySeed)).
     */
    private static final class LegacyPerlin {
        private final ImprovedNoise[] noiseLevels;
        private final double[] amplitudes;
        private final double lowestFreqInputFactor;
        private final double lowestFreqValueFactor;

        LegacyPerlin(LegacyRandom random, int firstOctave, double[] amplitudes) {
            this.amplitudes = amplitudes.clone();
            int size = amplitudes.length;
            int i = -firstOctave;
            this.noiseLevels = new ImprovedNoise[size];

            // forkPositional(): nextLong 1회 소비해 factory seed 생성.
            long factorySeed = random.nextLong();
            for (int k = 0; k < size; k++) {
                if (this.amplitudes[k] != 0.0) {
                    int octave = firstOctave + k;
                    int hash = ("octave_" + octave).hashCode();
                    this.noiseLevels[k] = new ImprovedNoise(new LegacyRandom(hash ^ factorySeed));
                }
            }
            this.lowestFreqInputFactor = Math.pow(2.0, -i);
            this.lowestFreqValueFactor = Math.pow(2.0, size - 1) / (Math.pow(2.0, size) - 1.0);
        }

        double getValue(double x, double y, double z) {
            double d  = 0.0;
            double d1 = this.lowestFreqInputFactor;
            double d2 = this.lowestFreqValueFactor;
            for (int idx = 0; idx < this.noiseLevels.length; idx++) {
                ImprovedNoise octave = this.noiseLevels[idx];
                if (octave != null) {
                    double v = octave.noise(wrap(x * d1), wrap(y * d1), wrap(z * d1));
                    d += this.amplitudes[idx] * v * d2;
                }
                d1 *= 2.0;
                d2 /= 2.0;
            }
            return d;
        }

        /** 바닐라 PerlinNoise.wrap — Mth.lfloor(long) 동치(Math.floor로 음수 안전). */
        private static double wrap(double value) {
            return value - Math.floor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
        }
    }
}
