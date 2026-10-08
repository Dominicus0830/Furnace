// 바닐라 Biome.getTemperature(pos)/coldEnoughToSnow/shouldMeltFrozenOceanIcebergSlightly 포팅 (높이 보정 + frozen modifier)
package org.domi.furnace.server.world.gen;

import java.util.List;
import org.domi.furnace.server.world.gen.carver.LegacyRandom;
import org.domi.furnace.server.world.gen.noise.PerlinSimplexNoise;

public final class BiomeClimate {

    private static final PerlinSimplexNoise TEMPERATURE_NOISE = new PerlinSimplexNoise(new LegacyRandom(1234L), List.of(0));
    private static final PerlinSimplexNoise FROZEN_TEMPERATURE_NOISE = new PerlinSimplexNoise(new LegacyRandom(3456L), List.of(-2, -1, 0));
    private static final PerlinSimplexNoise BIOME_INFO_NOISE = new PerlinSimplexNoise(new LegacyRandom(2345L), List.of(0));

    private BiomeClimate() {}

    /** Biome.getHeightAdjustedTemperature(pos) — temperature_modifier(frozen) 적용 후 y>80 고도 보정. */
    public static float temperature(float baseTemperature, boolean frozenModifier, int x, int y, int z) {
        float t = baseTemperature;
        if (frozenModifier) {
            double d = FROZEN_TEMPERATURE_NOISE.getValue(x * 0.05, z * 0.05, false) * 7.0;
            double e = BIOME_INFO_NOISE.getValue(x * 0.2, z * 0.2, false);
            if (d + e < 0.3) {
                double f = BIOME_INFO_NOISE.getValue(x * 0.09, z * 0.09, false);
                if (f < 0.8) t = 0.2F;
            }
        }
        if (y > 80) {
            float g = (float) (TEMPERATURE_NOISE.getValue((double) ((float) x / 8.0F), (double) ((float) z / 8.0F), false) * 8.0);
            return t - (g + (float) y - 80.0F) * 0.05F / 40.0F;
        }
        return t;
    }

    /** Biome.coldEnoughToSnow(pos) = !warmEnoughToRain(pos) = !(temperature >= 0.15). */
    public static boolean coldEnoughToSnow(float baseTemperature, boolean frozenModifier, int x, int y, int z) {
        return !(temperature(baseTemperature, frozenModifier, x, y, z) >= 0.15F);
    }

    /** Biome.shouldMeltFrozenOceanIcebergSlightly(pos) = temperature(pos) > 0.1. */
    public static boolean meltsIcebergSlightly(float baseTemperature, boolean frozenModifier, int x, int y, int z) {
        return temperature(baseTemperature, frozenModifier, x, y, z) > 0.1F;
    }
}
