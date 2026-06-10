// 바닐라 PerlinNoise(다중 옥타브 Perlin) 비트정확 포팅 — forkPositional/fromHashOf 기반 시딩
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.MathHelper;

/**
 * 바닐라 net.minecraft.world.level.levelgen.synth.PerlinNoise 비트정확 포팅.
 * useNewFactory=true 경로만 지원 (NormalNoise.create 가 사용하는 경로).
 * 옥타브 시딩: forkPositional() -> fromHashOf("octave_" + octaveIndex).
 */
public final class PerlinNoise {

    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final double[] amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    /**
     * 표준 팩토리 (NormalNoise 에서 호출).
     * firstOctave 와 amplitudes 배열을 받아 useNewFactory=true 경로로 생성.
     */
    public static PerlinNoise create(XoroshiroRandomSource random, int firstOctave, double[] amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes);
    }

    private PerlinNoise(XoroshiroRandomSource random, int firstOctave, double[] amplitudes) {
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes.clone();
        int size = amplitudes.length;
        int i = -firstOctave; // index of octave 0

        this.noiseLevels = new ImprovedNoise[size];
        XoroshiroRandomSource.XoroshiroPositionalRandomFactory positionalFactory = random.forkPositional();
        for (int i1 = 0; i1 < size; i1++) {
            if (this.amplitudes[i1] != 0.0) {
                int octaveIndex = firstOctave + i1;
                this.noiseLevels[i1] = new ImprovedNoise(positionalFactory.fromHashOf("octave_" + octaveIndex));
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, -i);
        this.lowestFreqValueFactor = Math.pow(2.0, size - 1) / (Math.pow(2.0, size) - 1.0);
        this.maxValue = edgeValue(2.0);
    }

    public double getValue(double x, double y, double z) {
        // 바닐라: getValue(x, y, z, 0.0, 0.0, false) -> useFixedY=false -> y 인자는 wrap(y*d1)
        double d = 0.0;
        double d1 = this.lowestFreqInputFactor;
        double d2 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise != null) {
                double d3 = improvedNoise.noise(
                    wrap(x * d1),
                    wrap(y * d1),
                    wrap(z * d1),
                    0.0, 0.0
                );
                d += this.amplitudes[i] * d3 * d2;
            }
            d1 *= 2.0;
            d2 /= 2.0;
        }

        return d;
    }

    double maxValue() {
        return this.maxValue;
    }

    private double edgeValue(double multiplier) {
        double d = 0.0;
        double d1 = this.lowestFreqValueFactor;
        for (int i = 0; i < this.noiseLevels.length; i++) {
            if (this.noiseLevels[i] != null) {
                d += this.amplitudes[i] * multiplier * d1;
            }
            d1 /= 2.0;
        }
        return d;
    }

    public static double wrap(double value) {
        return value - MathHelper.floor(value / 3.3554432E7 + 0.5) * 3.3554432E7;
    }
}
