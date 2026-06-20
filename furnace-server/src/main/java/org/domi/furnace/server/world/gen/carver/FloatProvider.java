// 바닐라 FloatProvider 포팅 — carver 설정 float 범위 샘플링
package org.domi.furnace.server.world.gen.carver;

import com.google.gson.JsonObject;

public interface FloatProvider {

    float sample(LegacyRandom random);

    static FloatProvider fromJson(JsonObject json) {
        String type = json.get("type").getAsString();
        return switch (type) {
            case "minecraft:uniform" -> {
                float min = json.get("min_inclusive").getAsFloat();
                float max = json.get("max_exclusive").getAsFloat();
                yield new Uniform(min, max);
            }
            case "minecraft:trapezoid" -> {
                float min     = json.get("min").getAsFloat();
                float max     = json.get("max").getAsFloat();
                float plateau = json.get("plateau").getAsFloat();
                yield new Trapezoid(min, max, plateau);
            }
            case "minecraft:clamped_normal" -> {
                float mean   = json.get("mean").getAsFloat();
                float stddev = json.get("standard_deviation").getAsFloat();
                float minV   = json.get("min").getAsFloat();
                float maxV   = json.get("max").getAsFloat();
                yield new ClampedNormal(mean, stddev, minV, maxV);
            }
            default -> throw new IllegalArgumentException("Unknown FloatProvider type: " + type);
        };
    }

    static FloatProvider constant(float value) {
        return r -> value;
    }

    record Uniform(float minInclusive, float maxExclusive) implements FloatProvider {
        @Override public float sample(LegacyRandom r) {
            return minInclusive + r.nextFloat() * (maxExclusive - minInclusive);
        }
    }

    record Trapezoid(float min, float max, float plateau) implements FloatProvider {
        @Override public float sample(LegacyRandom r) {
            float f  = max - min;
            float f1 = (f - plateau) / 2.0f;
            float f2 = f - f1;
            return min + r.nextFloat() * f2 + r.nextFloat() * f1;
        }
    }

    record ClampedNormal(float mean, float stddev, float min, float max) implements FloatProvider {
        @Override public float sample(LegacyRandom r) {
            float v;
            do {
                double v1, v2, s;
                do {
                    v1 = 2 * r.nextDouble() - 1;
                    v2 = 2 * r.nextDouble() - 1;
                    s  = v1 * v1 + v2 * v2;
                } while (s >= 1 || s == 0);
                double multiplier = Math.sqrt(-2 * Math.log(s) / s);
                v = (float)(v1 * multiplier * stddev + mean);
            } while (v < min || v > max);
            return v;
        }
    }
}
