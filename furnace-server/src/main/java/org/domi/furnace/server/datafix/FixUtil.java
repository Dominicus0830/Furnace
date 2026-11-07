// 데이터 픽서 공용 도움: 바닐라 Dynamic 연산(renameField·update)과 ComponentDataFixUtils·NamespacedSchema·AbstractUUIDFix 의 같은 일.
package org.domi.furnace.server.datafix;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

final class FixUtil {
    private FixUtil() {}

    /** NamespacedSchema.ensureNamespaced: 올바른 위치 문자열이면 "minecraft:" 를 붙인 꼴, 아니면 그대로. */
    static String ns(String s) {
        ResourceId id = ResourceId.tryParse(s);
        return id != null ? id.namespace() + ":" + id.path() : s;
    }

    /** 문자열 값(아니면 null). */
    static String str(NbtCompound c, String k) {
        return c.tags().get(k) instanceof String s ? s : null;
    }

    /** "id" 를 이름공간 붙여 읽는다(없으면 ""). */
    static String id(NbtCompound c) {
        String s = str(c, "id");
        return s == null ? "" : ns(s);
    }

    /** Dynamic.renameField: 있으면 옮긴다(새 키는 맨 뒤). */
    static void rename(NbtCompound c, String from, String to) {
        if (!c.contains(from)) return;
        Object v = c.tags().remove(from);
        c.tags().remove(to);
        c.tags().put(to, v);
    }

    /** Dynamic.update(k, f): 있을 때만 바꾼다(자리는 그대로). */
    static void update(NbtCompound c, String k, UnaryOperator<Object> f) {
        if (!c.contains(k)) return;
        c.tags().put(k, f.apply(c.tags().get(k)));
    }

    /** 문자열 바꾸기 표를 이름공간 붙인 키로 찾는다(바닐라 createRenamer(Map)). 없으면 원래 문자열. */
    static UnaryOperator<String> renamer(Map<String, String> map) {
        return s -> map.getOrDefault(ns(s), s);
    }

    /** createRenamer(from, to). */
    static UnaryOperator<String> renamer(String from, String to) {
        return s -> ns(s).equals(from) ? to : s;
    }

    // ---- 글 컴포넌트(ComponentDataFixUtils)

    /** GsonHelper.toStableString: 키 정렬, HTML 이스케이프 없음. */
    static String stableJson(JsonElement e) {
        StringWriter out = new StringWriter();
        try {
            write(new JsonWriter(out), e);
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
        return out.toString();
    }

    private static void write(JsonWriter w, JsonElement e) throws IOException {
        if (e == null || e.isJsonNull()) {
            w.nullValue();
        } else if (e.isJsonPrimitive()) {
            var p = e.getAsJsonPrimitive();
            if (p.isNumber()) w.value(p.getAsNumber());
            else if (p.isBoolean()) w.value(p.getAsBoolean());
            else w.value(p.getAsString());
        } else if (e.isJsonArray()) {
            w.beginArray();
            for (JsonElement x : e.getAsJsonArray()) write(w, x);
            w.endArray();
        } else {
            w.beginObject();
            for (String k : new java.util.TreeSet<>(e.getAsJsonObject().keySet())) {
                w.name(k);
                write(w, e.getAsJsonObject().get(k));
            }
            w.endObject();
        }
    }

    /** createPlainTextComponent: {"text":s}. */
    static String plainText(String s) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("text", s);
        return stableJson(o);
    }

    /** createTranslatableComponent: {"translate":key}. */
    static String translatable(String key) {
        com.google.gson.JsonObject o = new com.google.gson.JsonObject();
        o.addProperty("translate", key);
        return stableJson(o);
    }

    /** extractTranslationString: JSON 객체의 "translate" 문자열. */
    static Optional<String> translationKey(String json) {
        try {
            JsonElement e = JsonParser.parseString(json);
            if (e.isJsonObject()) {
                JsonElement t = e.getAsJsonObject().get("translate");
                if (t != null && t.isJsonPrimitive()) return Optional.of(t.getAsString());
            }
        } catch (JsonParseException | IllegalStateException ignored) {
            // 바닐라도 무시
        }
        return Optional.empty();
    }

    /** wrapLiteralStringAsComponent: 문자열이면 평문 컴포넌트 JSON, 아니면 그대로. */
    static Object wrapLiteral(Object v) {
        return v instanceof String s ? plainText(s) : v;
    }

    // ---- UUID(AbstractUUIDFix)

    static int[] uuidInts(long most, long least) {
        return new int[] {(int) (most >> 32), (int) most, (int) (least >> 32), (int) least};
    }

    /** replaceUUIDLeastMost: k+"Most"/k+"Least" 둘 다 0 이 아니면 지우고 to 에 int[4]. */
    static void uuidFromMostLeast(NbtCompound c, String k, String to) {
        long most = c.getLong(k + "Most"), least = c.getLong(k + "Least");
        if (most == 0L || least == 0L) return;
        c.tags().remove(k + "Most");
        c.tags().remove(k + "Least");
        c.tags().remove(to);
        c.tags().put(to, uuidInts(most, least));
    }

    /** replaceUUIDString: 문자열 UUID 를 int[4] 로(형식이 틀리면 그대로). */
    static void uuidFromString(NbtCompound c, String k, String to) {
        String s = str(c, k);
        if (s == null) return;
        try {
            UUID u = UUID.fromString(s);
            c.tags().remove(k);
            c.tags().remove(to);
            c.tags().put(to, uuidInts(u.getMostSignificantBits(), u.getLeastSignificantBits()));
        } catch (IllegalArgumentException ignored) {
            // 바닐라도 그대로 둔다
        }
    }

    /** {X,Y,Z} → int[3](ExtraDataFixUtils.fixBlockPos). 셋 중 하나라도 숫자가 아니면 그대로. */
    static Object blockPos(Object v) {
        if (v instanceof NbtCompound c && c.containsNumber("X") && c.containsNumber("Y") && c.containsNumber("Z")) {
            return new int[] {((Number) c.tags().get("X")).intValue(), ((Number) c.tags().get("Y")).intValue(), ((Number) c.tags().get("Z")).intValue()};
        }
        return v;
    }

    /** compound 원소 리스트를 하나씩 바꾼다(원소가 compound 가 아니면 그대로). */
    static NbtList mapCompounds(NbtList l, UnaryOperator<NbtCompound> f) {
        NbtList out = new NbtList(l.elementType());
        for (Object o : l.items()) out.add(o instanceof NbtCompound c ? f.apply(c) : o);
        return out;
    }

    static NbtList listOf(byte type) { return new NbtList(type); }

    static NbtList stringList(String... values) {
        NbtList l = new NbtList(NbtIo.TAG_STRING);
        for (String v : values) l.add(v);
        return l;
    }
}
