// noise_provider — LegacyNormalNoise 값을 [0,0.9999) 매핑해 states index 선택(random 0소비, vanilla 비트정확)
package org.domi.furnace.server.world.gen.feature;

import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.noise.LegacyNormalNoise;

/**
 * 바닐라 NoiseProvider.getState(NoiseBasedStateProvider.noise 사용):
 *   noiseValue = noise.getValue(x*scale, y*scale, z*scale)   // random 0소비
 *   d = clamp((1.0 + noiseValue) / 2.0, 0.0, 0.9999)
 *   return states[(int)(d * states.length)]
 */
public final class NoiseProvider implements StateProvider {

    final LegacyNormalNoise noise;
    final float scale;
    final int[] states;

    public NoiseProvider(long seed, int firstOctave, double[] amplitudes, float scale, int[] states) {
        this.noise = new LegacyNormalNoise(seed, firstOctave, amplitudes);
        this.scale = scale;
        this.states = states.clone();
    }

    @Override
    public int getState(WorldgenRandom random, int x, int y, int z) {
        double noiseValue = noise.getValue(x * (double) scale, y * (double) scale, z * (double) scale);
        return indexState(states, noiseValue);
    }

    /** 바닐라 NoiseProvider.getRandomState(possibleStates, delta) — list[(int)(clamp((1+v)/2,0,0.9999)*size)]. */
    static int indexState(int[] possibleStates, double noiseValue) {
        double d = MathHelper.clamp((1.0 + noiseValue) / 2.0, 0.0, 0.9999);
        return possibleStates[(int) (d * possibleStates.length)];
    }
}
