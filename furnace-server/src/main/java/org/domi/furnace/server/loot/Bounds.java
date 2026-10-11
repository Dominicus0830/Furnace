// 바닐라 MinMaxBounds(Ints·Doubles) — 수 또는 {min?, max?}.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

record Bounds(Double min, Double max) {

    static final Bounds ANY = new Bounds(null, null);

    static Bounds parse(JsonElement e) {
        if (e == null) return ANY;
        if (e.isJsonPrimitive()) {
            double v = e.getAsDouble();
            return new Bounds(v, v);
        }
        JsonObject o = e.getAsJsonObject();
        return new Bounds(o.has("min") ? o.get("min").getAsDouble() : null, o.has("max") ? o.get("max").getAsDouble() : null);
    }

    boolean isAny() { return min == null && max == null; }

    boolean matches(double v) { return (min == null || min <= v) && (max == null || v <= max); }
}
