// 네트워크 텍스트 NBT → 바닐라 텍스트 JSON 문자열(정규 값). NbtOps 로 읽힌 텍스트를 JsonOps 로 다시 쓰는 것과 같다:
// 문자열 태그 = 글자, 리스트 = 첫 원소 + 형제, {"": 값} 감싸기는 벗기고, 스타일 불리언(byte)은 true/false 로.
package org.domi.furnace.server.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.Set;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class TextNbtJson {
    private TextNbtJson() {}

    private static final Set<String> BOOLEAN_KEYS = Set.of("bold", "italic", "underlined", "strikethrough", "obfuscated", "interpret");

    static String toJson(Object tag) {
        return (String) PCodec.TEXT_FLAT.normalize(convert(tag, null).toString());
    }

    private static JsonElement convert(Object t, String key) {
        if (t instanceof String s) return new JsonPrimitive(s);
        if (t instanceof Number n) {
            if (key != null && BOOLEAN_KEYS.contains(key)) return new JsonPrimitive(n.byteValue() != 0);
            return new JsonPrimitive(n);
        }
        if (t instanceof NbtCompound c) {
            if (c.tags().size() == 1 && c.tags().containsKey("")) return convert(c.tags().get(""), key);
            JsonObject o = new JsonObject();
            for (var e : c.tags().entrySet()) o.add(e.getKey(), convert(e.getValue(), e.getKey()));
            return o;
        }
        JsonArray a = new JsonArray();
        if (t instanceof NbtList l) for (Object x : l.items()) a.add(convert(x, null));
        else for (Object x : Val.list(t)) a.add(convert(x, null));
        return a;
    }
}
