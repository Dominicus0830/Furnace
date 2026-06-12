// 바닐라 HeightProvider 포팅 — carver 시작 Y 결정 (above_bottom / absolute / uniform)
package org.domi.furnace.server.world.gen.carver;

import com.google.gson.JsonObject;

public interface HeightProvider {

    int sample(LegacyRandom random, int minY, int height);

    static HeightProvider fromJson(JsonObject json) {
        String type = json.get("type").getAsString();
        return switch (type) {
            case "minecraft:uniform" ->
                new Uniform(json.get("min_inclusive").getAsJsonObject(),
                            json.get("max_inclusive").getAsJsonObject());
            default -> throw new IllegalArgumentException("Unknown HeightProvider: " + type);
        };
    }

    static int resolveAnchor(JsonObject anchor, int minY) {
        if (anchor.has("above_bottom")) return minY + anchor.get("above_bottom").getAsInt();
        if (anchor.has("absolute"))     return anchor.get("absolute").getAsInt();
        throw new IllegalArgumentException("Unknown anchor: " + anchor);
    }

    record Uniform(JsonObject minAnchor, JsonObject maxAnchor) implements HeightProvider {
        @Override
        public int sample(LegacyRandom random, int minY, int height) {
            int min = resolveAnchor(minAnchor, minY);
            int max = resolveAnchor(maxAnchor, minY);
            if (min > max) return min;
            return min + random.nextInt(max - min + 1);
        }
    }
}
