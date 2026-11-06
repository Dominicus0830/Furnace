// 바닐라 JSON 직렬화(ComponentSerialization + JsonOps, Gson disableHtmlEscaping)와 같은 문자열을 만든다.
// 필드 순서는 바닐라 출력을 그대로 따른다(DFU RecordCodecBuilder 의 인코딩 순서): 내용 → extra → 스타일
// (hoverEvent, insertion, font, obfuscated, clickEvent, italic, underlined, strikethrough, color, bold), 호버는 contents → action.
package org.domi.furnace.api.text;

import java.util.List;
import java.util.UUID;

final class TextJson {

    private TextJson() {}

    static String encode(Text t) {
        StringBuilder sb = new StringBuilder(64);
        writeText(t, sb);
        return sb.toString();
    }

    private static void writeText(Text t, StringBuilder sb) {
        String collapsed = t.collapseToString();
        if (collapsed != null) {
            quote(collapsed, sb);
            return;
        }
        sb.append('{');
        boolean[] first = {true};
        switch (t.content()) {
            case Content.Literal l -> { field("text", first, sb); quote(l.text(), sb); }
            case Content.Translatable tr -> {
                field("translate", first, sb);
                quote(tr.key(), sb);
                if (tr.fallback() != null) { field("fallback", first, sb); quote(tr.fallback(), sb); }
                if (!tr.args().isEmpty()) {
                    field("with", first, sb);
                    sb.append('[');
                    for (int i = 0; i < tr.args().size(); i++) {
                        if (i > 0) sb.append(',');
                        writeArg(tr.args().get(i), sb);
                    }
                    sb.append(']');
                }
            }
            case Content.Keybind k -> { field("keybind", first, sb); quote(k.keybind(), sb); }
            case Content.Score s -> {
                field("score", first, sb);
                sb.append("{\"name\":");
                quote(s.name(), sb);
                sb.append(",\"objective\":");
                quote(s.objective(), sb);
                sb.append('}');
            }
            case Content.Selector s -> {
                field("selector", first, sb);
                quote(s.pattern(), sb);
                if (s.separator() != null) { field("separator", first, sb); writeText(s.separator(), sb); }
            }
            case Content.Nbt n -> {
                field("nbt", first, sb);
                quote(n.path(), sb);
                if (n.interpret()) { field("interpret", first, sb); sb.append("true"); }
                if (n.separator() != null) { field("separator", first, sb); writeText(n.separator(), sb); }
                switch (n.source()) {
                    case Content.NbtSource.Block b -> { field("block", first, sb); quote(b.pos(), sb); }
                    case Content.NbtSource.Entity e -> { field("entity", first, sb); quote(e.selector(), sb); }
                    case Content.NbtSource.Storage st -> { field("storage", first, sb); quote(st.id(), sb); }
                }
            }
        }
        if (!t.children().isEmpty()) {
            field("extra", first, sb);
            writeList(t.children(), sb);
        }
        writeStyle(t.style(), first, sb);
        sb.append('}');
    }

    private static void writeList(List<Text> list, StringBuilder sb) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            writeText(list.get(i), sb);
        }
        sb.append(']');
    }

    private static void writeArg(Object a, StringBuilder sb) {
        if (a instanceof Text t) writeText(t, sb);
        else if (a instanceof String s) quote(s, sb);
        else if (a instanceof Boolean b) sb.append(b);
        else sb.append(number((Number) a));
    }

    /** Gson 의 숫자 표기: 정수형은 그대로, Float/Double 은 Java toString. */
    static String number(Number n) {
        if (n instanceof Double d) return d.isNaN() || d.isInfinite() ? "\"" + d + "\"" : d.toString();
        if (n instanceof Float f) return f.isNaN() || f.isInfinite() ? "\"" + f + "\"" : f.toString();
        return n.toString();
    }

    private static void writeStyle(Style s, boolean[] first, StringBuilder sb) {
        if (s.isEmpty()) return;
        if (s.hoverEvent() != null) {
            field("hoverEvent", first, sb);
            sb.append("{\"contents\":");
            switch (s.hoverEvent()) {
                case HoverEvent.ShowText st -> writeText(st.text(), sb);
                case HoverEvent.ShowItem si -> {
                    sb.append("{\"id\":");
                    quote(si.item(), sb);
                    if (si.count() != 1) sb.append(",\"count\":").append(si.count());
                    sb.append('}');
                }
                case HoverEvent.ShowEntity se -> {
                    sb.append("{\"type\":");
                    quote(se.type(), sb);
                    sb.append(",\"id\":");
                    int[] u = uuidInts(se.id());
                    sb.append('[').append(u[0]).append(',').append(u[1]).append(',').append(u[2]).append(',').append(u[3]).append(']');
                    if (se.name() != null) { sb.append(",\"name\":"); writeText(se.name(), sb); }
                    sb.append('}');
                }
            }
            sb.append(",\"action\":");
            quote(s.hoverEvent().action(), sb);
            sb.append('}');
        }
        if (s.insertion() != null) { field("insertion", first, sb); quote(s.insertion(), sb); }
        if (s.font() != null) { field("font", first, sb); quote(s.font(), sb); }
        bool("obfuscated", s.decoration(TextDecoration.OBFUSCATED), first, sb);
        if (s.clickEvent() != null) {
            field("clickEvent", first, sb);
            sb.append("{\"action\":");
            quote(s.clickEvent().action().key(), sb);
            sb.append(",\"value\":");
            quote(s.clickEvent().value(), sb);
            sb.append('}');
        }
        bool("italic", s.decoration(TextDecoration.ITALIC), first, sb);
        bool("underlined", s.decoration(TextDecoration.UNDERLINED), first, sb);
        bool("strikethrough", s.decoration(TextDecoration.STRIKETHROUGH), first, sb);
        if (s.color() != null) { field("color", first, sb); quote(s.color().serialize(), sb); }
        bool("bold", s.decoration(TextDecoration.BOLD), first, sb);
    }

    static int[] uuidInts(UUID id) {
        long m = id.getMostSignificantBits(), l = id.getLeastSignificantBits();
        return new int[]{(int) (m >> 32), (int) m, (int) (l >> 32), (int) l};
    }

    private static void bool(String name, TriState v, boolean[] first, StringBuilder sb) {
        if (v == TriState.NOT_SET) return;
        field(name, first, sb);
        sb.append(v == TriState.TRUE);
    }

    private static void field(String name, boolean[] first, StringBuilder sb) {
        if (!first[0]) sb.append(',');
        first[0] = false;
        sb.append('"').append(name).append("\":");
    }

    /** Gson(disableHtmlEscaping) 문자열 이스케이프. */
    static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case ' ' -> sb.append("\\u2028");
                case ' ' -> sb.append("\\u2029");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }
}
