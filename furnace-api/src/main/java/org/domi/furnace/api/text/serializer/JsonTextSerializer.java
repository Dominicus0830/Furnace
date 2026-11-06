// 바닐라 텍스트 JSON ↔ Text. 읽기는 문자열·배열(첫 원소 + 나머지는 형제)·객체의 모든 내용 종류와 스타일 필드를 받고,
// 예전 형식(hoverEvent.value, SNBT 문자열 contents)도 너그럽게 읽는다. 쓰기는 Text.toJson() 그대로(바닐라와 같은 바이트).
package org.domi.furnace.api.text.serializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.api.text.TriState;

public final class JsonTextSerializer {

    private static final JsonTextSerializer INSTANCE = new JsonTextSerializer();

    private static final Pattern SNBT_ID = Pattern.compile("(?:^|[{,])\\s*\"?id\"?\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern SNBT_COUNT = Pattern.compile("\"?[Cc]ount\"?\\s*:\\s*(\\d+)");
    private static final Pattern SNBT_TYPE = Pattern.compile("\"?type\"?\\s*:\\s*[\"']([^\"']+)[\"']");
    private static final Pattern SNBT_UUID = Pattern.compile("\"?id\"?\\s*:\\s*[\"']([0-9a-fA-F-]{36})[\"']");
    private static final Pattern SNBT_NAME = Pattern.compile("\"?name\"?\\s*:\\s*'((?:[^'\\\\]|\\\\.)*)'");

    private JsonTextSerializer() {}

    public static JsonTextSerializer json() { return INSTANCE; }

    /** Text.toJson() 와 같다. */
    public String serialize(Text text) { return text.toJson(); }

    /**
     * 바닐라 텍스트 JSON 을 Text 로.
     *
     * @throws IllegalArgumentException JSON 문법 오류나 알 수 없는 내용일 때
     */
    public Text deserialize(String json) { return toText(MiniJson.parse(json)); }

    /** 명령 인자용: text 의 start 부터 lenient JSON 값 하나를 Text 로 읽는다(바닐라 ComponentArgument). 읽은 뒤 위치를 함께 돌려준다. */
    public record Parsed(Text text, int end) {}

    public Parsed deserializePrefix(String text, int start) {
        MiniJson.Prefix p = MiniJson.parsePrefix(text, start);
        return new Parsed(toText(p.value()), p.end());
    }

    /** 명령 인자용(바닐라 StyleArgument): start 부터 lenient JSON 객체 하나를 스타일로 읽는다. */
    public record StyleParsed(Style style, int end) {}

    @SuppressWarnings("unchecked")
    public StyleParsed deserializeStylePrefix(String text, int start) {
        MiniJson.Prefix p = MiniJson.parsePrefix(text, start);
        if (!(p.value() instanceof Map<?, ?> m)) throw new IllegalArgumentException("Not a JSON object: " + p.value());
        return new StyleParsed(style((Map<String, Object>) m), p.end());
    }

    /** JSON 객체 문자열을 스타일로(알 수 없는 키는 무시). */
    @SuppressWarnings("unchecked")
    public Style deserializeStyle(String json) {
        Object o = MiniJson.parse(json);
        if (!(o instanceof Map<?, ?> m)) throw new IllegalArgumentException("Not a JSON object: " + o);
        return style((Map<String, Object>) m);
    }

    // ------------------------------------------------------------------ 변환

    @SuppressWarnings("unchecked")
    private static Text toText(Object o) {
        if (o == null) throw new IllegalArgumentException("텍스트 자리에 null 이 있다");
        if (o instanceof String s) return Text.text(s);
        if (o instanceof Number || o instanceof Boolean) return Text.text(String.valueOf(o));
        if (o instanceof List<?> list) {
            if (list.isEmpty()) return Text.empty();
            Text first = toText(list.get(0));
            if (list.size() == 1) return first;
            List<Text> kids = new ArrayList<>(first.children());
            for (int i = 1; i < list.size(); i++) kids.add(toText(list.get(i)));
            return first.children(kids);
        }
        Map<String, Object> m = (Map<String, Object>) o;
        Content content = content(m);
        Style style = style(m);
        List<Text> kids = List.of();
        if (m.get("extra") instanceof List<?> extra) {
            kids = new ArrayList<>(extra.size());
            for (Object e : extra) kids.add(toText(e));
        }
        return Text.of(content, style, kids);
    }

    @SuppressWarnings("unchecked")
    private static Content content(Map<String, Object> m) {
        if (m.containsKey("text")) return new Content.Literal(str(m.get("text")));
        if (m.containsKey("translate")) {
            List<Object> args = new ArrayList<>();
            if (m.get("with") instanceof List<?> with) {
                for (Object a : with) {
                    if (a instanceof String || a instanceof Number || a instanceof Boolean) args.add(a);
                    else args.add(toText(a));
                }
            }
            Object fb = m.get("fallback");
            return new Content.Translatable(str(m.get("translate")), fb == null ? null : str(fb), args);
        }
        if (m.containsKey("keybind")) return new Content.Keybind(str(m.get("keybind")));
        if (m.containsKey("score")) {
            Map<String, Object> sc = (Map<String, Object>) m.get("score");
            return new Content.Score(str(sc.get("name")), str(sc.get("objective")));
        }
        if (m.containsKey("selector")) {
            Object sep = m.get("separator");
            return new Content.Selector(str(m.get("selector")), sep == null ? null : toText(sep));
        }
        if (m.containsKey("nbt")) {
            Object sep = m.get("separator");
            Content.NbtSource src;
            if (m.containsKey("block")) src = new Content.NbtSource.Block(str(m.get("block")));
            else if (m.containsKey("entity")) src = new Content.NbtSource.Entity(str(m.get("entity")));
            else if (m.containsKey("storage")) src = new Content.NbtSource.Storage(str(m.get("storage")));
            else throw new IllegalArgumentException("nbt 텍스트에 block/entity/storage 가 없다");
            return new Content.Nbt(str(m.get("nbt")), Boolean.TRUE.equals(bool(m.get("interpret"))), sep == null ? null : toText(sep), src);
        }
        throw new IllegalArgumentException("알 수 없는 텍스트 내용: " + m.keySet());
    }

    @SuppressWarnings("unchecked")
    private static Style style(Map<String, Object> m) {
        Style s = Style.empty();
        if (m.get("color") instanceof String c) {
            TextColor col = TextColor.parse(c);
            if (col != null) s = s.color(col);
        }
        for (TextDecoration d : TextDecoration.values()) {
            Boolean b = bool(m.get(d.key()));
            if (b != null) s = s.decoration(d, TriState.of(b));
        }
        if (m.get("insertion") instanceof String ins) s = s.insertion(ins);
        if (m.get("font") instanceof String f) s = s.font(f);
        Object click = m.containsKey("clickEvent") ? m.get("clickEvent") : m.get("click_event");
        if (click instanceof Map<?, ?> cm) {
            ClickEvent.Action a = ClickEvent.Action.byKey(str(cm.get("action")));
            Object v = cm.get("value");
            if (a != null && v != null) s = s.clickEvent(new ClickEvent(a, str(v)));
        }
        Object hover = m.containsKey("hoverEvent") ? m.get("hoverEvent") : m.get("hover_event");
        if (hover instanceof Map<?, ?> hm) {
            HoverEvent h = hover((Map<String, Object>) hm);
            if (h != null) s = s.hoverEvent(h);
        }
        return s;
    }

    // ------------------------------------------------------------------ 호버(새 형식 contents / 옛 형식 value)

    @SuppressWarnings("unchecked")
    private static HoverEvent hover(Map<String, Object> h) {
        String action = h.get("action") instanceof String a ? a : "";
        Object contents = h.containsKey("contents") ? h.get("contents") : h.get("value");
        if (contents == null) return null;
        switch (action) {
            case "show_text" -> {
                if (contents instanceof String s && looksLikeJson(s)) {
                    try {
                        return HoverEvent.showText(toText(MiniJson.parse(s)));
                    } catch (IllegalArgumentException e) {
                        return HoverEvent.showText(Text.text(s));
                    }
                }
                return HoverEvent.showText(toText(contents));
            }
            case "show_item" -> {
                if (contents instanceof Map<?, ?> cm) {
                    Map<String, Object> c = (Map<String, Object>) cm;
                    if (c.get("id") == null) return null;
                    int count = c.get("count") instanceof Number n ? n.intValue() : c.get("Count") instanceof Number n2 ? n2.intValue() : 1;
                    Object comps = c.containsKey("components") ? c.get("components") : c.get("tag");
                    return new HoverEvent.ShowItem(str(c.get("id")), count, comps == null ? null : comps instanceof String cs ? cs : snbt(comps));
                }
                if (contents instanceof String s) {
                    Matcher idm = SNBT_ID.matcher(s);
                    if (idm.find()) {
                        Matcher cm = SNBT_COUNT.matcher(s);
                        return new HoverEvent.ShowItem(idm.group(1), cm.find() ? Integer.parseInt(cm.group(1)) : 1, null);
                    }
                    if (!s.isEmpty() && !s.startsWith("{")) return new HoverEvent.ShowItem(s, 1, null);
                }
                return null;
            }
            case "show_entity" -> {
                String type, uuid;
                Text name = null;
                Object idObj;
                if (contents instanceof Map<?, ?> cm) {
                    Map<String, Object> c = (Map<String, Object>) cm;
                    type = c.get("type") instanceof String t ? t : null;
                    idObj = c.get("id");
                    if (c.get("name") != null) name = toText(c.get("name"));
                    UUID id = uuid(idObj);
                    return type == null || id == null ? null : new HoverEvent.ShowEntity(type, id, name);
                }
                if (contents instanceof String s) {
                    Matcher tm = SNBT_TYPE.matcher(s), im = SNBT_UUID.matcher(s);
                    if (!tm.find() || !im.find()) return null;
                    type = tm.group(1);
                    uuid = im.group(1);
                    Matcher nm = SNBT_NAME.matcher(s);
                    if (nm.find()) {
                        try {
                            name = toText(MiniJson.parse(nm.group(1).replace("\\'", "'").replace("\\\\", "\\")));
                        } catch (IllegalArgumentException ignored) {
                            name = null;
                        }
                    }
                    return new HoverEvent.ShowEntity(type, UUID.fromString(uuid), name);
                }
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    private static boolean looksLikeJson(String s) {
        String t = s.trim();
        return t.startsWith("{") || t.startsWith("[") || t.startsWith("\"");
    }

    /** 문자열 UUID 또는 int 4개 배열. */
    private static UUID uuid(Object o) {
        try {
            if (o instanceof String s) return UUID.fromString(s);
            if (o instanceof List<?> l && l.size() == 4) {
                long a = ((Number) l.get(0)).intValue() & 0xFFFFFFFFL, b = ((Number) l.get(1)).intValue() & 0xFFFFFFFFL;
                long c = ((Number) l.get(2)).intValue() & 0xFFFFFFFFL, d = ((Number) l.get(3)).intValue() & 0xFFFFFFFFL;
                return new UUID(a << 32 | b, c << 32 | d);
            }
        } catch (RuntimeException ignored) {
            // 아래에서 null
        }
        return null;
    }

    /** JSON 값을 SNBT 비슷한 문자열로(키는 따옴표로 감싼다 — SNBT 가 허용한다). 아이템 컴포넌트 보관용. */
    private static String snbt(Object o) {
        StringBuilder sb = new StringBuilder();
        snbt(o, sb);
        return sb.toString();
    }

    private static void snbt(Object o, StringBuilder sb) {
        if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(String.valueOf(e.getKey()), sb);
                sb.append(':');
                snbt(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(',');
                snbt(l.get(i), sb);
            }
            sb.append(']');
        } else if (o instanceof String s) {
            quote(s, sb);
        } else {
            sb.append(o);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') sb.append('\\');
            sb.append(c);
        }
        sb.append('"');
    }

    private static String str(Object o) {
        if (o == null) throw new IllegalArgumentException("값이 없다");
        return o instanceof String s ? s : String.valueOf(o);
    }

    /** true/false 또는 "true"/"false" 문자열, 그 밖에는 null. */
    private static Boolean bool(Object o) {
        if (o instanceof Boolean b) return b;
        if (o instanceof String s) {
            if (s.equalsIgnoreCase("true")) return true;
            if (s.equalsIgnoreCase("false")) return false;
        }
        return null;
    }
}
