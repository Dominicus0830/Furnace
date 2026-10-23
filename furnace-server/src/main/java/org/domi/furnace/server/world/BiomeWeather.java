// 바이옴 날씨 성질(Biome.ClimateSettings): 기본 온도·frozen 보정·강수 유무 — 바닐라 바이옴 JSON 에서. 강수 판정(Biome.getPrecipitationAt·
// shouldFreeze·shouldSnow 의 온도 부분)을 한곳에 둔다.
package org.domi.furnace.server.world;

import com.google.gson.JsonObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.domi.furnace.server.world.gen.BiomeClimate;
import org.domi.furnace.server.world.gen.WorldgenData;

public final class BiomeWeather {

    /** 강수 종류(Biome.Precipitation). */
    public static final String NONE = "none", RAIN = "rain", SNOW = "snow";

    public record Climate(float temperature, boolean frozen, boolean hasPrecipitation) {

        /** Biome.warmEnoughToRain(pos): 높이 보정 온도 ≥ 0.15. */
        public boolean warmEnoughToRain(int x, int y, int z) { return !BiomeClimate.coldEnoughToSnow(temperature, frozen, x, y, z); }

        /** Biome.getPrecipitationAt(pos). */
        public String precipitationAt(int x, int y, int z) {
            if (!hasPrecipitation) return NONE;
            return warmEnoughToRain(x, y, z) ? RAIN : SNOW;
        }
    }

    private static final Climate DEFAULT = new Climate(0.5F, false, true);
    private static final Map<String, Climate> CACHE = new ConcurrentHashMap<>();
    private static volatile WorldgenData data;

    private BiomeWeather() {}

    /** 바이옴 이름(minecraft:…)의 날씨 성질. 모르면 평원 같은 기본값. */
    public static Climate of(String biome) {
        if (biome == null) return DEFAULT;
        return CACHE.computeIfAbsent(biome, BiomeWeather::load);
    }

    private static Climate load(String biome) {
        try {
            WorldgenData d = data;
            if (d == null) data = d = WorldgenData.load("worldgen/1.21.1");
            JsonObject b = d.biome(biome);
            float t = b.has("temperature") ? b.get("temperature").getAsFloat() : 0.5F;
            boolean frozen = b.has("temperature_modifier") && "frozen".equals(b.get("temperature_modifier").getAsString());
            boolean precip = !b.has("has_precipitation") || b.get("has_precipitation").getAsBoolean();
            return new Climate(t, frozen, precip);
        } catch (RuntimeException e) {
            return DEFAULT;
        }
    }
}
