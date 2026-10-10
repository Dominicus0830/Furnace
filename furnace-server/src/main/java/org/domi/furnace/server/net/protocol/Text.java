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
    // 스타일(바닐라 Style) — null 이면 상속.
    private String color;
    private Boolean bold, italic, underlined, strikethrough, obfuscated;
    private String clickAction, clickValue;
    private Text hoverText;
    private String insertion;

    private Text(String literal, String key, List<Object> args) {
        this.literal = literal;
        this.key = key;
        this.args = args;
    }

    public static Text literal(String s) { return new Text(s, null, List.of()); }

    public static Text translatable(String key, Object... args) { return new Text(null, key, List.of(args)); }

    /** 빈 literal(바닐라 Component.empty) — 형제를 담는 그릇. */
    public static Text empty() { return literal(""); }

    /** 이름 있는 색(ChatFormatting 소문자 이름: red, gray, green, yellow, ...). */
    public Text color(String name) { this.color = name; return this; }

    public Text bold(boolean v) { this.bold = v; return this; }

    public Text italic(boolean v) { this.italic = v; return this; }

    public Text underlined(boolean v) { this.underlined = v; return this; }

    public Text strikethrough(boolean v) { this.strikethrough = v; return this; }

    public Text obfuscated(boolean v) { this.obfuscated = v; return this; }

    /** 클릭 이벤트(open_url, run_command, suggest_command, copy_to_clipboard 등). */
    public Text click(String action, String value) { this.clickAction = action; this.clickValue = value; return this; }

    public Text hover(Text text) { this.hoverText = text; return this; }

    public Text insertion(String s) { this.insertion = s; return this; }

    private boolean hasStyle() {
        return color != null || bold != null || italic != null || underlined != null || strikethrough != null || obfuscated != null
                || clickAction != null || hoverText != null || insertion != null;
    }

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
        if (color != null) o.addProperty("color", color);
        if (bold != null) o.addProperty("bold", bold);
        if (italic != null) o.addProperty("italic", italic);
        if (underlined != null) o.addProperty("underlined", underlined);
        if (strikethrough != null) o.addProperty("strikethrough", strikethrough);
        if (obfuscated != null) o.addProperty("obfuscated", obfuscated);
        if (clickAction != null) {
            JsonObject c = new JsonObject();
            c.addProperty("action", clickAction);
            c.addProperty("value", clickValue);
            o.add("clickEvent", c);
        }
        if (hoverText != null) {
            JsonObject h = new JsonObject();
            h.addProperty("action", "show_text");
            h.add("contents", hoverText.toJsonObject());
            o.add("hoverEvent", h);
        }
        if (insertion != null) o.addProperty("insertion", insertion);
        if (!siblings.isEmpty()) {
            JsonArray extra = new JsonArray();
            for (Text t : siblings) extra.add(t.toJsonObject());
            o.add("extra", extra);
        }
        return o;
    }

    /** JSON 문자열. 스타일·형제 없는 literal 은 바닐라처럼 문자열 하나로 접는다. */
    public String toJson() {
        if (literal != null && siblings.isEmpty() && !hasStyle()) return new com.google.gson.JsonPrimitive(literal).toString();
        return toJsonObject().toString();
    }

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
        if (color != null) c.putString("color", color);
        if (bold != null) c.putByte("bold", (byte) (bold ? 1 : 0));
        if (italic != null) c.putByte("italic", (byte) (italic ? 1 : 0));
        if (underlined != null) c.putByte("underlined", (byte) (underlined ? 1 : 0));
        if (strikethrough != null) c.putByte("strikethrough", (byte) (strikethrough ? 1 : 0));
        if (obfuscated != null) c.putByte("obfuscated", (byte) (obfuscated ? 1 : 0));
        if (clickAction != null) {
            NbtCompound ce = new NbtCompound();
            ce.putString("action", clickAction);
            ce.putString("value", clickValue);
            c.putCompound("clickEvent", ce);
        }
        if (hoverText != null) {
            NbtCompound he = new NbtCompound();
            he.putString("action", "show_text");
            he.putCompound("contents", hoverText.toNbt());
            c.putCompound("hoverEvent", he);
        }
        if (insertion != null) c.putString("insertion", insertion);
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
