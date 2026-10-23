// 바닐라 LevelBasedValue — 인챈트 레벨에 따른 값(상수·선형·제한·분수·제곱·표).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public interface LevelBasedValue {

    float calculate(int level);

    static LevelBasedValue parse(JsonElement e) {
        if (e.isJsonPrimitive()) {
            float v = e.getAsFloat();
            return level -> v;
        }
        JsonObject o = e.getAsJsonObject();
        return switch (LootJson.id(o, "type")) {
            case "minecraft:constant" -> { float v = LootJson.req(o, "value").getAsFloat(); yield level -> v; }
            case "minecraft:linear" -> {
                float base = LootJson.req(o, "base").getAsFloat(), per = LootJson.req(o, "per_level_above_first").getAsFloat();
                yield level -> base + per * (float) (level - 1);
            }
            case "minecraft:clamped" -> {
                LevelBasedValue v = parse(LootJson.req(o, "value"));
                float min = LootJson.req(o, "min").getAsFloat(), max = LootJson.req(o, "max").getAsFloat();
                if (max <= min) throw new LootParseException("Max must be larger than min, min: " + min + ", max: " + max);
                yield level -> Mth.clamp(v.calculate(level), min, max);
            }
            case "minecraft:fraction" -> {
                LevelBasedValue num = parse(LootJson.req(o, "numerator")), den = parse(LootJson.req(o, "denominator"));
                yield level -> { float d = den.calculate(level); return d == 0.0f ? 0.0f : num.calculate(level) / d; };
            }
            case "minecraft:levels_squared" -> { float added = LootJson.req(o, "added").getAsFloat(); yield level -> (float) (level * level) + added; }
            case "minecraft:lookup" -> {
                List<Float> values = new ArrayList<>();
                for (JsonElement x : LootJson.req(o, "values").getAsJsonArray()) values.add(x.getAsFloat());
                LevelBasedValue fallback = parse(LootJson.req(o, "fallback"));
                yield level -> level <= values.size() ? values.get(level - 1) : fallback.calculate(level);
            }
            default -> throw new LootParseException("모르는 레벨 값: " + o.get("type"));
        };
    }
}
