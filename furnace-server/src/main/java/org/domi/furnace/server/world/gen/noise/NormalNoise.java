// 바닐라 NormalNoise(두 PerlinNoise 합성) 비트정확 포팅 — INPUT_FACTOR/valueFactor 산술 동일
package org.domi.furnace.server.world.gen.noise;

/**
 * 바닐라 net.minecraft.world.level.levelgen.synth.NormalNoise 비트정확 포팅.
 * 두 개의 PerlinNoise (first, second) 를 합산 후 valueFactor 로 스케일.
 * INPUT_FACTOR = 1.0181268882175227, valueFactor = 0.16666... / expectedDeviation(span).
 */
public final class NormalNoise {

    private static final double INPUT_FACTOR = 1.0181268882175227;

    private final double valueFactor;
    private final PerlinNoise first;
    private final PerlinNoise second;

    /**
     * 표준 팩토리: double[] 가변 인자 형태.
     * 바닐라 NormalNoise.create(random, firstOctave, double... amplitudes) 에 대응.
     */
    public static NormalNoise create(XoroshiroRandomSource random, int firstOctave, double... amplitudes) {
        return new NormalNoise(random, firstOctave, amplitudes);
    }

    private NormalNoise(XoroshiroRandomSource random, int firstOctave, double[] amplitudes) {
        // 바닐라 useLegacyNetherBiome=false 경로: PerlinNoise.create (useNewFactory=true)
        this.first = PerlinNoise.create(random, firstOctave, amplitudes);
        this.second = PerlinNoise.create(random, firstOctave, amplitudes);

        // valueFactor 계산: 바닐라와 동일한 로직
        // i1 = min non-zero index, i2 = max non-zero index (0-based in amplitude array)
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
}
