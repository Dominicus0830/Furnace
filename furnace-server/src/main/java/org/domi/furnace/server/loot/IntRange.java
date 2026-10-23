// 바닐라 IntRange — 정수(min=max) 또는 {min?, max?}(수 공급자).
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public record IntRange(NumberProvider min, NumberProvider max) {

    public static IntRange parse(JsonElement e) {
        if (e.isJsonPrimitive()) {
            NumberProvider c = new NumberProvider.Constant(e.getAsInt());
            return new IntRange(c, c);
        }
        JsonObject o = e.getAsJsonObject();
        return new IntRange(o.has("min") ? NumberProvider.parse(o.get("min")) : null, o.has("max") ? NumberProvider.parse(o.get("max")) : null);
    }

    public int clamp(LootContext ctx, int value) {
        if (min == null && max == null) return value;
        if (min == null) return Math.min(max.getInt(ctx), value);
        if (max == null) return Math.max(min.getInt(ctx), value);
        return Mth.clamp(value, min.getInt(ctx), max.getInt(ctx));
    }

    public boolean matches(LootContext ctx, int value) {
        if (min == null && max == null) return true;
        if (min == null) return value <= max.getInt(ctx);
        if (max == null) return value >= min.getInt(ctx);
        return value >= min.getInt(ctx) && value <= max.getInt(ctx);
    }
}
