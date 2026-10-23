// 바닐라 FloatProvider(feature 설정용) — constant / uniform(min_inclusive,max_exclusive) / clamped_normal(mean,deviation,min,max)
package org.domi.furnace.server.world.gen.feature;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public interface FeatureFloatProvider {

    float sample(WorldgenRandom random);

    static FeatureFloatProvider fromJson(JsonElement el) {
        if (el.isJsonPrimitive()) {
            float v = el.getAsFloat();
            return r -> v;
        }
        JsonObject o = el.getAsJsonObject();
        String type = o.get("type").getAsString();
        return switch (type) {
            case "minecraft:constant" -> {
                float v = o.get("value").getAsFloat();
                yield r -> v;
            }
            case "minecraft:uniform" -> new Uniform(o.get("min_inclusive").getAsFloat(), o.get("max_exclusive").getAsFloat());
            case "minecraft:clamped_normal" -> new ClampedNormal(o.get("mean").getAsFloat(), o.get("deviation").getAsFloat(),
                o.get("min").getAsFloat(), o.get("max").getAsFloat());
            default -> throw new IllegalArgumentException("알 수 없는 FloatProvider: " + o);
        };
    }

    /** Mth.randomBetween(random, min, max) = nextFloat * (max - min) + min */
    record Uniform(float min, float max) implements FeatureFloatProvider {
        @Override public float sample(WorldgenRandom r) { return r.nextFloat() * (max - min) + min; }
    }

    /** ClampedNormalFloat: clamp(mean + (float) nextGaussian * deviation, min, max) */
    record ClampedNormal(float mean, float deviation, float min, float max) implements FeatureFloatProvider {
        @Override public float sample(WorldgenRandom r) { return sampleStatic(r, mean, deviation, min, max); }

        static float sampleStatic(WorldgenRandom r, float mean, float deviation, float min, float max) {
            float v = mean + (float) r.nextGaussian() * deviation;
            return v < min ? min : Math.min(v, max);
        }
    }

    /** Mth.clampedMap(float) */
    static float clampedMap(float v, float inMin, float inMax, float outMin, float outMax) {
        float t = (v - inMin) / (inMax - inMin);
        if (t < 0.0F) return outMin;
        return t > 1.0F ? outMax : outMin + t * (outMax - outMin);
    }

    /** Mth.clampedMap(double) */
    static double clampedMap(double v, double inMin, double inMax, double outMin, double outMax) {
        double t = (v - inMin) / (inMax - inMin);
        if (t < 0.0) return outMin;
        return t > 1.0 ? outMax : outMin + t * (outMax - outMin);
    }
}
