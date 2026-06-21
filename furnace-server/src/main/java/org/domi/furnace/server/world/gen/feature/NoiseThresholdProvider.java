// noise_threshold_provider — LegacyNormalNoise 값으로 low/high/default state 분기(소비순서 vanilla 동일)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.LegacyNormalNoise;

/**
 * 바닐라 NoiseThresholdProvider.getState 비트정확:
 *   noise = noise.getValue(x*scale, y*scale, z*scale)            // random 0소비
 *   noise < threshold ? lowStates[nextInt(size)]
 *                     : (nextFloat() < highChance ? highStates[nextInt(size)] : defaultState)
 * Util.getRandom(list, r) = list.get(r.nextInt(list.size())).
 */
public final class NoiseThresholdProvider implements StateProvider {

    private final LegacyNormalNoise noise;
    private final float scale;
    private final float threshold;
    private final float highChance;
    private final int defaultState;
    private final int[] lowStates;
    private final int[] highStates;

    public NoiseThresholdProvider(long seed, int firstOctave, double[] amplitudes,
                                  float scale, float threshold, float highChance,
                                  int defaultState, int[] lowStates, int[] highStates) {
        this.noise = new LegacyNormalNoise(seed, firstOctave, amplitudes);
        this.scale = scale;
        this.threshold = threshold;
        this.highChance = highChance;
        this.defaultState = defaultState;
        this.lowStates = lowStates.clone();
        this.highStates = highStates.clone();
    }

    @Override
    public int getState(WorldgenRandom random, int x, int y, int z) {
        double noiseValue = noise.getValue(x * (double) scale, y * (double) scale, z * (double) scale);
        if (noiseValue < threshold) {
            return lowStates[random.nextInt(lowStates.length)];
        }
        return random.nextFloat() < highChance
            ? highStates[random.nextInt(highStates.length)]
            : defaultState;
    }
}
