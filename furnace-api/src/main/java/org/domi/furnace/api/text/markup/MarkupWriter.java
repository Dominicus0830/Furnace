// Text → 마크업 문자열(패키지 내부). 트리를 (마디, 실제 스타일) 조각으로 평탄화한 뒤, 이웃 조각의 스타일 차이만큼 태그를 열고 닫는다.
// 다시 해석하면 글자와 글자별 스타일이 같은 Text 가 나온다(트리 모양은 다를 수 있다).
package org.domi.furnace.api.text.markup;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.api.text.ClickEvent;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.HoverEvent;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextColor;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.api.text.Texts;
import org.domi.furnace.api.text.TriState;

final class MarkupWriter {

    /** 열린 태그 하나(종류 + 값). */
    private record Attr(int kind, Object value) {}

    private static final int COLOR = 0, CLICK = 6, HOVER = 7, INSERT = 8, FONT = 9; // 1..5 는 꾸밈(ordinal+1)

    private final StringBuilder sb = new StringBuilder();
    private final List<Attr> open = new ArrayList<>();
    private final boolean closeAll;

    private MarkupWriter(boolean closeAll) { this.closeAll = closeAll; }

    static String write(Text root, boolean closeAll) {
        MarkupWriter w = new MarkupWriter(closeAll);
        Texts.forEachSegment(root, w::segment);
        if (closeAll) w.closeFrom(0);
        return w.sb.toString();
    }

    // ------------------------------------------------------------------ 조각

    private void segment(Text node, Style eff) {
        Content c = node.content();
        if (c instanceof Content.Literal l && l.text().isEmpty()) return;
        List<Attr> want = attrs(eff);
        for (int i = 0; i < open.size(); i++) {
            if (!want.contains(open.get(i))) {
                closeFrom(i);
                break;
            }
        }
        for (Attr a : want) {
            if (!open.contains(a)) {
                open.add(a);
                openTag(a);
            }
        }
        content(c);
    }

    private static List<Attr> attrs(Style s) {
        List<Attr> list = new ArrayList<>(4);
        if (s.color() != null) list.add(new Attr(COLOR, s.color()));
        for (TextDecoration d : TextDecoration.values()) {
            TriState v = s.decoration(d);
            if (v != TriState.NOT_SET) list.add(new Attr(d.ordinal() + 1, v == TriState.TRUE));
        }
        if (s.clickEvent() != null) list.add(new Attr(CLICK, s.clickEvent()));
        if (s.hoverEvent() != null) list.add(new Attr(HOVER, s.hoverEvent()));
        if (s.insertion() != null) list.add(new Attr(INSERT, s.insertion()));
        if (s.font() != null) list.add(new Attr(FONT, s.font()));
        return list;
    }

    private void closeFrom(int idx) {
        while (open.size() > idx) {
            Attr a = open.remove(open.size() - 1);
            sb.append("</").append(closeName(a)).append('>');
        }
    }

    private static String closeName(Attr a) {
        return switch (a.kind) {
            case COLOR -> colorName((TextColor) a.value);
            case CLICK -> "click";
            case HOVER -> "hover";
            case INSERT -> "insert";
            case FONT -> "font";
            default -> (Boolean.TRUE.equals(a.value) ? "" : "!") + TextDecoration.values()[a.kind - 1].key();
        };
    }

    private static String colorName(TextColor c) {
        if (c.isNamed()) return c.name();
        char[] h = new char[7];
        h[0] = '#';
        for (int i = 0; i < 6; i++) h[6 - i] = Character.forDigit(c.value() >> (i * 4) & 0xF, 16);
        return new String(h);
    }

    private void openTag(Attr a) {
        sb.append('<');
        switch (a.kind) {
            case COLOR -> sb.append(colorName((TextColor) a.value));
            case CLICK -> {
                ClickEvent e = (ClickEvent) a.value;
                sb.append("click:").append(e.action().key()).append(':');
                arg(sb, e.value());
            }
            case HOVER -> hover((HoverEvent) a.value);
            case INSERT -> {
                sb.append("insert:");
                arg(sb, (String) a.value);
            }
            case FONT -> {
                sb.append("font:");
                arg(sb, (String) a.value);
            }
            default -> sb.append(closeName(a));
        }
        sb.append('>');
    }

    private void hover(HoverEvent h) {
        switch (h) {
            case HoverEvent.ShowText t -> {
                sb.append("hover:show_text:");
                arg(sb, write(t.text(), true));
            }
            case HoverEvent.ShowItem i -> {
                sb.append("hover:show_item:");
                arg(sb, i.item());
                sb.append(':').append(i.count());
                if (i.components() != null) {
                    sb.append(':');
                    arg(sb, i.components());
                }
            }
            case HoverEvent.ShowEntity e -> {
                sb.append("hover:show_entity:");
                arg(sb, e.type());
                sb.append(':').append(e.id());
                if (e.name() != null) {
                    sb.append(':');
                    arg(sb, write(e.name(), true));
                }
            }
        }
    }

    // ------------------------------------------------------------------ 내용

    private void content(Content c) {
        switch (c) {
            case Content.Literal l -> escape(sb, l.text());
            case Content.Translatable t -> {
                sb.append('<');
                if (t.fallback() != null) {
                    sb.append("lang_or:");
                    arg(sb, t.key());
                    sb.append(':');
                    arg(sb, t.fallback());
                } else {
                    sb.append("lang:");
                    arg(sb, t.key());
                }
                for (Object a : t.args()) {
                    sb.append(':');
                    if (a instanceof Text at) {
                        arg(sb, write(at, true));
                    } else if (a instanceof String s) {
                        StringBuilder tmp = new StringBuilder();
                        escape(tmp, s);
                        arg(sb, tmp.toString());
                    } else {
                        arg(sb, String.valueOf(a));
                    }
                }
                sb.append('>');
            }
            case Content.Keybind k -> {
                sb.append("<key:");
                arg(sb, k.keybind());
                sb.append('>');
            }
            case Content.Score s -> {
                sb.append("<score:");
                arg(sb, s.name());
                sb.append(':');
                arg(sb, s.objective());
                sb.append('>');
            }
            case Content.Selector s -> {
                sb.append("<selector:");
                arg(sb, s.pattern());
                if (s.separator() != null) {
                    sb.append(':');
                    arg(sb, write(s.separator(), true));
                }
                sb.append('>');
            }
            case Content.Nbt n -> {
                sb.append("<nbt:");
                switch (n.source()) {
                    case Content.NbtSource.Block b -> { sb.append("block:"); arg(sb, b.pos()); }
                    case Content.NbtSource.Entity e -> { sb.append("entity:"); arg(sb, e.selector()); }
                    case Content.NbtSource.Storage s -> { sb.append("storage:"); arg(sb, s.id()); }
                }
                sb.append(':');
                arg(sb, n.path());
                if (n.separator() != null) {
                    sb.append(':');
                    arg(sb, write(n.separator(), true));
                }
                if (n.interpret()) sb.append(":interpret");
                sb.append('>');
            }
        }
    }

    // ------------------------------------------------------------------ 이스케이프

    /** 글자 이스케이프: '<' → \<, 그리고 뒤가 '<'·'\'·끝인 '\' 는 \\ 로. */
    static void escape(StringBuilder out, String s) {
        int n = s.length();
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '<') {
                out.append("\\<");
            } else if (c == '\\') {
                char next = i + 1 < n ? s.charAt(i + 1) : '<';
                out.append(next == '<' || next == '\\' ? "\\\\" : "\\");
            } else {
                out.append(c);
            }
        }
    }

    /** 태그 인자: 특수 문자가 있으면 '...' 로 감싸고 ' 와 \ 를 이스케이프. */
    static void arg(StringBuilder out, String v) {
        boolean quote = v.isEmpty();
        for (int i = 0; i < v.length() && !quote; i++) {
            char c = v.charAt(i);
            quote = c == ':' || c == '\'' || c == '"' || c == '<' || c == '>' || c == '\\' || Character.isWhitespace(c);
        }
        if (!quote) {
            out.append(v);
            return;
        }
        out.append('\'');
        for (int i = 0; i < v.length(); i++) {
            char c = v.charAt(i);
            if (c == '\'' || c == '\\') out.append('\\');
            out.append(c);
        }
        out.append('\'');
    }
}
