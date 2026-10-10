// 최소 텍스트 컴포넌트(바닐라 Component) — literal·translatable(+인자)·이어 붙인 형제. 로그인 단계는 JSON, 1.20.3+ 의 설정·플레이
// 단계는 NBT 로 보낸다. 서버 로그에는 바닐라 Component.getString 처럼 en_us 로 번역한 평문을 쓴다.
package org.domi.furnace.server.net.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;

public final class Text {

    private final String literal;      // literal 이면 내용, translatable 이면 null
    private final String key;          // translatable 키
    private final List<Object> args;   // String 또는 Text
    private final List<Text> siblings = new ArrayList<>();

    private Text(String literal, String key, List<Object> args) {
        this.literal = literal;
        this.key = key;
        this.args = args;
    }

    public static Text literal(String s) { return new Text(s, null, List.of()); }

    public static Text translatable(String key, Object... args) { return new Text(null, key, List.of(args)); }

    /** 형제(extra) 를 덧붙인 자신(바닐라 MutableComponent.append). */
    public Text append(Text sibling) {
        siblings.add(sibling);
        return this;
    }

    public JsonObject toJsonObject() {
        JsonObject o = new JsonObject();
        if (literal != null) {
            o.addProperty("text", literal);
        } else {
            o.addProperty("translate", key);
            if (!args.isEmpty()) {
                JsonArray with = new JsonArray();
                for (Object a : args) {
                    if (a instanceof Text t) with.add(t.toJsonObject()); else with.add(String.valueOf(a));
                }
                o.add("with", with);
            }
        }
        if (!siblings.isEmpty()) {
            JsonArray extra = new JsonArray();
            for (Text t : siblings) extra.add(t.toJsonObject());
            o.add("extra", extra);
        }
        return o;
    }

    public String toJson() { return toJsonObject().toString(); }

    public NbtCompound toNbt() {
        NbtCompound c = new NbtCompound();
        if (literal != null) {
            c.putString("text", literal);
        } else {
            c.putString("translate", key);
            if (!args.isEmpty()) {
                NbtList with = new NbtList(NbtIo.TAG_COMPOUND);
                for (Object a : args) with.add(a instanceof Text t ? t.toNbt() : literal(String.valueOf(a)).toNbt());
                c.putList("with", with);
            }
        }
        if (!siblings.isEmpty()) {
            NbtList extra = new NbtList(NbtIo.TAG_COMPOUND);
            for (Text t : siblings) extra.add(t.toNbt());
            c.putList("extra", extra);
        }
        return c;
    }

    /** 바닐라 Component.getString — 서버 언어(en_us)로 번역한 평문. */
    public String getString() {
        StringBuilder sb = new StringBuilder();
        if (literal != null) {
            sb.append(literal);
        } else {
            String[] a = new String[args.size()];
            for (int i = 0; i < a.length; i++) a[i] = args.get(i) instanceof Text t ? t.getString() : String.valueOf(args.get(i));
            sb.append(Language.format(key, a));
        }
        for (Text t : siblings) sb.append(t.getString());
        return sb.toString();
    }

    @Override
    public String toString() { return getString(); }
}
