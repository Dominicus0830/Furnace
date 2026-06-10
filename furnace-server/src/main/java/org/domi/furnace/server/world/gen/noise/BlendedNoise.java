// 바닐라 old_blended_noise 3D 지형 노이즈 비트정확 포팅 (레거시 PerlinNoise 3개 조합)
package org.domi.furnace.server.world.gen.noise;

import org.domi.furnace.server.world.gen.MathHelper;

import java.util.stream.IntStream;

/**
 * 바닐라 net.minecraft.world.level.levelgen.synth.BlendedNoise 비트정확 포팅.
 * minLimitNoise, maxLimitNoise (각 16옥타브), mainNoise (8옥타브)를
 * createLegacyForBlendedNoise 레거시 경로로 생성.
 * compute() 로직을 calculate(int,int,int) 에 그대로 이식.
 */
public final class BlendedNoise {

    private final PerlinNoise minLimitNoise;
    private final PerlinNoise maxLimitNoise;
    private final PerlinNoise mainNoise;
    private final double xzScale;
    private final double yScale;
    private final double xzFactor;
    private final double yFactor;
    private final double smearScaleMultiplier;
    private final double xzMultiplier;
    private final double yMultiplier;
    private final double maxValue;

    public BlendedNoise(XoroshiroRandomSource random,
                        double xzScale, double yScale,
                        double xzFactor, double yFactor,
                        double smearScaleMultiplier) {
        this.minLimitNoise = PerlinNoise.createLegacyForBlendedNoise(random, IntStream.rangeClosed(-15, 0));
        this.maxLimitNoise = PerlinNoise.createLegacyForBlendedNoise(random, IntStream.rangeClosed(-15, 0));
        this.mainNoise     = PerlinNoise.createLegacyForBlendedNoise(random, IntStream.rangeClosed(-7, 0));
        this.xzScale = xzScale;
        this.yScale  = yScale;
        this.xzFactor = xzFactor;
        this.yFactor  = yFactor;
        this.smearScaleMultiplier = smearScaleMultiplier;
        this.xzMultiplier = 684.412 * xzScale;
        this.yMultiplier  = 684.412 * yScale;
        this.maxValue = minLimitNoise.maxBrokenValue(this.yMultiplier);
    }

    /**
     * 바닐라 BlendedNoise.compute(FunctionContext) 의 샘플 로직.
     * 정수 블록 좌표를 받아 동일한 double 값을 반환.
     */
    public double calculate(int blockX, int blockY, int blockZ) {
        double d  = blockX * this.xzMultiplier;
        double d1 = blockY * this.yMultiplier;
        double d2 = blockZ * this.xzMultiplier;
        double d3 = d  / this.xzFactor;
        double d4 = d1 / this.yFactor;
        double d5 = d2 / this.xzFactor;
        double d6 = this.yMultiplier * this.smearScaleMultiplier;
        double d7 = d6 / this.yFactor;

        double d8  = 0.0; // minLimit accumulator
        double d9  = 0.0; // maxLimit accumulator
        double d10 = 0.0; // main accumulator
        double d11 = 1.0;

        // mainNoise 8옥타브 루프 (octave 0..7)
        for (int i = 0; i < 8; i++) {
            ImprovedNoise octaveNoise = this.mainNoise.getOctaveNoise(i);
            if (octaveNoise != null) {
                d10 += octaveNoise.noise(
                    PerlinNoise.wrap(d3 * d11),
                    PerlinNoise.wrap(d4 * d11),
                    PerlinNoise.wrap(d5 * d11),
                    d7 * d11,
                    d4 * d11
                ) / d11;
            }
            d11 /= 2.0;
        }

        double d12 = (d10 / 10.0 + 1.0) / 2.0;
        boolean flag1 = d12 >= 1.0;
        boolean flag2 = d12 <= 0.0;
        d11 = 1.0;

        // minLimit/maxLimit 16옥타브 루프 (octave 0..15)
        for (int i = 0; i < 16; i++) {
            double d13 = PerlinNoise.wrap(d  * d11);
            double d14 = PerlinNoise.wrap(d1 * d11);
            double d15 = PerlinNoise.wrap(d2 * d11);
            double d16 = d6 * d11;

            if (!flag1) {
                ImprovedNoise oct = this.minLimitNoise.getOctaveNoise(i);
                if (oct != null) {
                    d8 += oct.noise(d13, d14, d15, d16, d1 * d11) / d11;
                }
            }
            if (!flag2) {
                ImprovedNoise oct = this.maxLimitNoise.getOctaveNoise(i);
                if (oct != null) {
                    d9 += oct.noise(d13, d14, d15, d16, d1 * d11) / d11;
                }
            }
            d11 /= 2.0;
        }

        return MathHelper.clampedLerp(d8 / 512.0, d9 / 512.0, d12) / 128.0;
    }

    public double maxValue() {
        return this.maxValue;
    }
}
