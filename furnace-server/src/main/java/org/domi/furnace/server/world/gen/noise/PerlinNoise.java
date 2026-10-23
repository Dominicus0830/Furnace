// 바닐라 PerlinNoise(다중 옥타브 Perlin) 비트정확 포팅 — forkPositional/fromHashOf 및 레거시 순차 시딩 지원
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.MathHelper;

import java.util.stream.IntStream;

/**
 * 바닐라 net.minecraft.world.level.levelgen.synth.PerlinNoise 비트정확 포팅.
 * useNewFactory=true: NormalNoise 경로 (forkPositional -> fromHashOf).
 * useNewFactory=false: BlendedNoise 레거시 경로 (순차 new ImprovedNoise(random)).
 */
public final class PerlinNoise {

    private final ImprovedNoise[] noiseLevels;
    private final int firstOctave;
    private final double[] amplitudes;
    private final double lowestFreqValueFactor;
    private final double lowestFreqInputFactor;
    private final double maxValue;

    /**
     * 표준 팩토리 (NormalNoise 에서 호출). useNewFactory=true 경로.
     */
    public static PerlinNoise create(NoiseRandom random, int firstOctave, double[] amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, true);
    }

    /**
     * 레거시 팩토리 (BlendedNoise 에서 호출). useNewFactory=false 경로.
     * 바닐라의 createLegacyForBlendedNoise(random, octaves) 에 대응.
     * octaves: e.g. IntStream.rangeClosed(-15, 0) or IntStream.rangeClosed(-7, 0).
     */
    public static PerlinNoise createLegacyForBlendedNoise(NoiseRandom random, IntStream octaves) {
        // makeAmplitudes: firstOctave = -maxOctave, size = maxOctave - minOctave + 1
        int[] arr = octaves.sorted().toArray();
        int minOctave = arr[0];  // e.g. -15
        int maxOctave = arr[arr.length - 1]; // e.g. 0
        int firstOctave = minOctave;
        int size = maxOctave - minOctave + 1;
        double[] amps = new double[size];
        for (int o : arr) {
            amps[o - minOctave] = 1.0;
        }
        return new PerlinNoise(random, firstOctave, amps, false);
    }

    /** 바닐라 PerlinNoise.createLegacyForLegacyNetherBiome — legacy_random_source 차원의 temperature/vegetation 노이즈. */
    public static PerlinNoise createLegacyForLegacyNetherBiome(NoiseRandom random, int firstOctave, double[] amplitudes) {
        return new PerlinNoise(random, firstOctave, amplitudes, false);
    }

    private PerlinNoise(NoiseRandom random, int firstOctave, double[] amplitudes, boolean useNewFactory) {
        this.firstOctave = firstOctave;
        this.amplitudes = amplitudes.clone();
        int size = amplitudes.length;
        int zeroOctaveIndex = -firstOctave; // index within noiseLevels[] that corresponds to octave 0

        this.noiseLevels = new ImprovedNoise[size];

        if (useNewFactory) {
            // 바닐라 useNewFactory=true: forkPositional -> fromHashOf("octave_N")
            PositionalRandom positionalFactory = random.forkPositional();
            for (int i = 0; i < size; i++) {
                if (this.amplitudes[i] != 0.0) {
                    int octaveIndex = firstOctave + i;
                    this.noiseLevels[i] = new ImprovedNoise(positionalFactory.fromHashOf("octave_" + octaveIndex));
                }
            }
        } else {
            // 바닐라 useNewFactory=false (레거시): 순차 new ImprovedNoise(random)
            // 바닐라는 octave-0 자리에 무조건 ImprovedNoise 하나 생성(RNG 소비)한 뒤 amplitude 확인.
            ImprovedNoise zeroOctaveNoise = new ImprovedNoise(random);
            if (zeroOctaveIndex >= 0 && zeroOctaveIndex < size) {
                double amp = this.amplitudes[zeroOctaveIndex];
                if (amp != 0.0) {
                    this.noiseLevels[zeroOctaveIndex] = zeroOctaveNoise;
                }
            }
            // zeroOctaveIndex-1 부터 0까지 내려가며 처리
            for (int i = zeroOctaveIndex - 1; i >= 0; i--) {
                if (i < size) {
                    double amp = this.amplitudes[i];
                    if (amp != 0.0) {
                        this.noiseLevels[i] = new ImprovedNoise(random);
                    } else {
                        // skipOctave: consumeCount(262)
                        random.consumeCount(262);
                    }
                } else {
                    random.consumeCount(262);
                }
            }
        }

        this.lowestFreqInputFactor = Math.pow(2.0, -zeroOctaveIndex);
        this.lowestFreqValueFactor = Math.pow(2.0, size - 1) / (Math.pow(2.0, size) - 1.0);
        this.maxValue = edgeValue(2.0);
    }

    public double getValue(double x, double y, double z) {
        // 바닐라: getValue(x, y, z, 0.0, 0.0, false) -> useFixedY=false -> y 인자는 wrap(y*d1)
        return getValue(x, y, z, 0.0, 0.0, false);
    }

    /**
     * 바닐라 @Deprecated getValue(x,y,z,yScale,yMax,useFixedY) — BlendedNoise 내부 루프가 사용.
     */
    public double getValue(double x, double y, double z, double yScale, double yMax, boolean useFixedY) {
        double d = 0.0;
        double d1 = this.lowestFreqInputFactor;
        double d2 = this.lowestFreqValueFactor;

        for (int i = 0; i < this.noiseLevels.length; i++) {
            ImprovedNoise improvedNoise = this.noiseLevels[i];
            if (improvedNoise != null) {
                double yArg = useFixedY ? -improvedNoise.yo : wrap(y * d1);
                double d3 = improvedNoise.noise(
                    wrap(x * d1),
                    yArg,
                    wrap(z * d1),
                    yScale * d1,
                    yMax * d1
                );
                d += this.amplitudes[i] * d3 * d2;
            }
            d1 *= 2.0;
            d2 /= 2.0;
        }

        return d;
    }

    /**
     * 바닐라 getOctaveNoise(int octave) — BlendedNoise 루프에서 직접 ImprovedNoise 를 꺼내 씀.
     */
    public ImprovedNoise getOctaveNoise(int octave) {
        return this.noiseLevels[this.noiseLevels.length - 1 - octave];
    }

    /**
     * 바닐라 maxBrokenValue(yMultiplier) — BlendedNoise.maxValue 계산.
     */
    public double maxBrokenValue(double yMultiplier) {
        return edgeValue(yMultiplier + 2.0);
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
