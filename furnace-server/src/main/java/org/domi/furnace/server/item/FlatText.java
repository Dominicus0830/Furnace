// 저장용 텍스트 문자열(바닐라 ComponentSerialization.flatCodec): 읽기는 Gson 관대 파서 → 텍스트 코덱,
// 쓰기는 컴포넌트 JSON 을 GsonHelper.toStableString 으로 — 객체 키를 재귀적으로 정렬한 한 줄 JSON(HTML 이스케이프 없음).
package org.domi.furnace.server.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;

public final class FlatText {
    private FlatText() {}

    /** 저장 문자열 → 바닐라가 다시 쓸 정규 문자열. */
    public static String canonical(String stored) {
        return stable(parse(stored));
    }

    /** 저장 문자열 → 텍스트. */
    public static Text parse(String stored) {
        JsonElement e;
        try {
            e = JsonParser.parseString(stored);   // Gson 기본(관대): 따옴표 없는 낱말도 문자열
        } catch (RuntimeException ex) {
            throw new CodecException("Failed to parse text component: " + ex.getMessage());
        }
        if (e == null || e.isJsonNull()) throw new CodecException("Failed to parse text component: null");
        try {
            return JsonTextSerializer.json().deserialize(e.toString());
        } catch (RuntimeException ex) {
            throw new CodecException("Failed to parse text component: " + ex.getMessage());
        }
    }

    /** 텍스트 → 바닐라 저장 문자열. */
    public static String stable(Text t) {
        return sorted(JsonParser.parseString(t.toJson())).toString();
    }

    private static JsonElement sorted(JsonElement e) {
        if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            List<String> keys = new ArrayList<>(o.keySet());
            Collections.sort(keys);
            JsonObject r = new JsonObject();
            for (String k : keys) r.add(k, sorted(o.get(k)));
            return r;
        }
        if (e.isJsonArray()) {
            JsonArray r = new JsonArray();
            for (JsonElement x : e.getAsJsonArray()) r.add(sorted(x));
            return r;
        }
        return e;
    }
}
