// 바닐라 StatePropertiesPredicate — 속성 이름 → 정확한 값 또는 {min?, max?}.
package org.domi.furnace.server.loot;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

public final class StateProperties {
    private StateProperties() {}

    public static boolean matches(Map<String, JsonElement> props, LootBlockState state) {
        for (var p : props.entrySet()) {
            String actual = state.properties().get(p.getKey());
            if (actual == null) return false;
            JsonElement want = p.getValue();
            if (want.isJsonPrimitive()) {
                if (!want.getAsString().equals(actual)) return false;
            } else {
                JsonObject r = want.getAsJsonObject();
                if (!inRange(actual, r.has("min") ? r.get("min").getAsString() : null, r.has("max") ? r.get("max").getAsString() : null)) return false;
            }
        }
        return true;
    }

    private static boolean inRange(String actual, String min, String max) {
        try {
            int v = Integer.parseInt(actual);
            return (min == null || v >= Integer.parseInt(min)) && (max == null || v <= Integer.parseInt(max));
        } catch (NumberFormatException e) {
            return (min == null || actual.compareTo(min) >= 0) && (max == null || actual.compareTo(max) <= 0);
        }
    }
}
