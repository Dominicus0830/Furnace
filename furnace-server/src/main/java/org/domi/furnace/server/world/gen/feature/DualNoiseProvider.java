// dual_noise_provider — slowNoise로 variety 개수 list 구성 후 fastNoise로 최종 index(random 0소비, vanilla 비트정확)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.LegacyNormalNoise;

/**
 * 바닐라 DualNoiseProvider extends NoiseProvider. getState:
 *   slowVal = slowNoise.getValue(x*slowScale, y*slowScale, z*slowScale)
 *   i = (int) Mth.clampedMap(slowVal, -1, 1, varietyMin, varietyMax + 1)
 *   for k in 0..i-1: list += indexState(states, slowNoise at pos.offset(k*54545,0,k*34234))
 *   return indexState(list, fastNoise.getValue(x*scale, y*scale, z*scale))
 * fastNoise = 상위 NoiseProvider.noise(seed/noise params, scale). slowNoise = (같은 seed, slow_noise params, slowScale).
 * random 0소비.
 */
public final class DualNoiseProvider implements StateProvider {

    private final NoiseProvider fast;
    private final LegacyNormalNoise slowNoise;
    private final float slowScale;
    private final int varietyMin;
    private final int varietyMax;

    public DualNoiseProvider(int varietyMin, int varietyMax,
                             long slowSeed, int slowFirstOctave, double[] slowAmplitudes, float slowScale,
                             long seed, int firstOctave, double[] amplitudes, float scale,
                             int[] states) {
        this.fast = new NoiseProvider(seed, firstOctave, amplitudes, scale, states);
        this.slowNoise = new LegacyNormalNoise(slowSeed, slowFirstOctave, slowAmplitudes);
        this.slowScale = slowScale;
        this.varietyMin = varietyMin;
        this.varietyMax = varietyMax;
    }

    @Override
    public int getState(WorldgenRandom random, int x, int y, int z) {
        int i = (int) MathHelper.clampedMap(slowNoiseValue(x, y, z), -1.0, 1.0,
            (double) varietyMin, (double) (varietyMax + 1));
        int[] list = new int[i];
        for (int k = 0; k < i; k++) {
            list[k] = NoiseProvider.indexState(fast.states, slowNoiseValue(x + k * 54545, y, z + k * 34234));
        }
        double fastNoiseValue = fast.noise.getValue(
            x * (double) fast.scale, y * (double) fast.scale, z * (double) fast.scale);
        return NoiseProvider.indexState(list, fastNoiseValue);
    }

    private double slowNoiseValue(int x, int y, int z) {
        return slowNoise.getValue(x * (double) slowScale, y * (double) slowScale, z * (double) slowScale);
    }
}
