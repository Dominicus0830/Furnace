// 텍스트·스타일 ↔ 저장 NBT 값(바닐라 ComponentSerialization.CODEC / Style.Serializer.CODEC + NbtOps).
// 쓰기는 네트워크 NBT 바이트를 그대로 값 객체로 읽어 쓰고, 읽기는 NBT 값을 JSON 으로 옮겨 텍스트 파서에 맡긴다.
package org.domi.furnace.server.scores;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.serializer.JsonTextSerializer;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class TextTags {

    private TextTags() {}

    /** 텍스트 → NBT 값(String 또는 compound 등). */
    static Object textToTag(Text t) { return NbtIo.readUnnamed(t.networkNbt()); }

    /** 스타일 → compound. */
    static NbtCompound styleToTag(Style s) { return (NbtCompound) NbtIo.readUnnamed(s.networkNbt()); }

    /** NBT 값 → 텍스트(형식이 틀리면 null). */
    static Text tagToText(Object tag) {
        try {
            return JsonTextSerializer.json().deserialize(toJson(tag).toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** compound → 스타일(형식이 틀리면 null). */
    static Style tagToStyle(NbtCompound tag) {
        try {
            return JsonTextSerializer.json().deserializeStyle(toJson(tag).toString());
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonElement toJson(Object o) {
        if (o instanceof String s) return new JsonPrimitive(s);
        if (o instanceof Byte b) return new JsonPrimitive(b != 0);   // 텍스트 NBT 의 byte 는 참거짓뿐이다
        if (o instanceof Number n) return new JsonPrimitive(n);
        if (o instanceof int[] a) {
            JsonArray arr = new JsonArray();
            for (int v : a) arr.add(v);
            return arr;
        }
        if (o instanceof NbtList l) {
            JsonArray arr = new JsonArray();
            for (Object item : l.items()) arr.add(toJson(item));
            return arr;
        }
        if (o instanceof NbtCompound c) {
            // NbtOps 이종 리스트 감싸개 {"": 값}
            if (c.tags().size() == 1 && c.tags().containsKey("")) return toJson(c.tags().get(""));
            JsonObject obj = new JsonObject();
            for (var e : c.tags().entrySet()) obj.add(e.getKey(), toJson(e.getValue()));
            return obj;
        }
        throw new IllegalArgumentException("텍스트로 옮길 수 없는 NBT: " + o);
    }
}
