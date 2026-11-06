// 전리품 JSON 공용 도우미.
package org.domi.furnace.server.loot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.domi.furnace.server.data.ResourceId;

final class LootJson {
    private LootJson() {}

    static JsonElement req(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null) throw new LootParseException("No key " + key + " in " + o);
        return e;
    }

    /** 네임스페이스를 붙인 id 문자열. */
    static String id(JsonObject o, String key) { return ResourceId.parse(req(o, key).getAsString()).toString(); }

    static String id(String s) { return ResourceId.parse(s).toString(); }

    static <T> List<T> list(JsonObject o, String key, Function<JsonElement, T> f) {
        JsonElement e = o.get(key);
        if (e == null) return List.of();
        List<T> out = new ArrayList<>();
        if (e.isJsonArray()) for (JsonElement x : (JsonArray) e) out.add(f.apply(x));
        else out.add(f.apply(e));
        return List.copyOf(out);
    }

    static boolean bool(JsonObject o, String key, boolean def) { return o.has(key) ? o.get(key).getAsBoolean() : def; }

    static int integer(JsonObject o, String key, int def) { return o.has(key) ? o.get(key).getAsInt() : def; }
}
