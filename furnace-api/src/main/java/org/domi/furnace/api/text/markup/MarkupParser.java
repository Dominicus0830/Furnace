// 마크업 해석기(패키지 내부) — 한 번 훑으며 스택으로 바로 Text 를 만든다(중간 트리 없음).
// 열린 태그마다 프레임(자식 목록)을 쌓고, 닫힐 때 태그 종류대로 부모 목록에 붙인다. 그라디언트 같은 변형 태그는 닫힐 때 자식 전체를 받는다.
package org.domi.furnace.api.text.markup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;

final class MarkupParser implements Context {

    private static final int MAX_NEST = 16;        // 컨텍스트 중첩(호버 안 호버 등) 한도
    private static final int MAX_INLINE = 16;      // 원문 치환 중첩 한도

    private static final class Frame {
        final String key;
        final String display;
        final Tag tag;
        final ArrayList<Text> out = new ArrayList<>();

        Frame(String key, String display, Tag tag) {
            this.key = key;
            this.display = display;
            this.tag = tag;
        }
    }

    private final String original;
    private final TagResolver resolver;
    private final boolean strict;
    private final int nest;
    private final TagScanner scanner = new TagScanner();
    private final ArrayList<Frame> stack = new ArrayList<>(4);
    private final ArrayList<Text> root = new ArrayList<>(4);
    private final StringBuilder text = new StringBuilder();
    private int curPos = -1;

    private MarkupParser(String original, TagResolver resolver, boolean strict, int nest) {
        this.original = original;
        this.resolver = resolver;
        this.strict = strict;
        this.nest = nest;
    }

    /** 원문 치환(Placeholder.parsed)까지 처리하고 해석한다. */
    static Text parseFull(String input, TagResolver resolver, boolean strict, int nest) {
        if (nest > MAX_NEST) throw new MarkupParseException("마크업 중첩이 너무 깊다", input, -1);
        String s = expand(input, resolver, 0);
        MarkupParser p = new MarkupParser(input, resolver, strict, nest);
        p.feed(s, 0);
        return p.finish();
    }

    // ------------------------------------------------------------------ 해석

    private void feed(String s, int inline) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                if (i + 1 < n) {
                    char d = s.charAt(i + 1);
                    if (d == '<' || d == '\\') {
                        text.append(d);
                        i += 2;
                        continue;
                    }
                }
                text.append(c);
                i++;
                continue;
            }
            if (c != '<') {
                int j = i + 1;
                while (j < n) {
                    char d = s.charAt(j);
                    if (d == '<' || d == '\\') break;
                    j++;
                }
                text.append(s, i, j);
                i = j;
                continue;
            }
            if (!scanner.scan(s, i)) {
                text.append('<');
                i++;
                continue;
            }
            int end = scanner.end;
            int kind = scanner.kind;
            String name = scanner.name(s);
            if (kind != TagScanner.CLOSE && name.equals("pre")) {
                int close = indexOfIgnoreCase(s, "</pre>", end);
                if (close < 0) {
                    if (strict) throw error("닫히지 않은 태그: <pre>", i);
                    text.append(s, end, n);
                    i = n;
                } else {
                    text.append(s, end, close);
                    i = close + 6;
                }
                continue;
            }
            boolean handled = kind == TagScanner.CLOSE ? handleClose(name, i) : handleOpen(name, kind, i, inline);
            if (!handled) text.append(s, i, end);
            i = end;
        }
    }

    private boolean handleOpen(String name, int kind, int pos, int inline) {
        ArgumentQueue args = scanner.queue();
        curPos = pos;
        Tag tag;
        try {
            tag = resolver.resolve(name, args, this);
        } catch (MarkupParseException e) {
            throw e.withLocation(original, pos);
        }
        if (tag == null) return false;
        switch (tag) {
            case Tag.PreProcess p -> {
                if (inline >= MAX_INLINE) return false;
                feed(p.value(), inline + 1);
            }
            case Tag.Reset r -> {
                flushText();
                closeFrames(0);
            }
            case Tag.Inserting ins -> {
                flushText();
                if (!ins.allowsChildren() || kind == TagScanner.SELF) out().add(ins.value());
                else stack.add(new Frame(canon(name), name, tag));
            }
            case Tag.Styling st -> {
                flushText();
                if (kind != TagScanner.SELF) stack.add(new Frame(canon(name), name, tag));
            }
            case Tag.Modifying m -> {
                flushText();
                if (kind != TagScanner.SELF) stack.add(new Frame(canon(name), name, tag));
            }
        }
        return true;
    }

    private boolean handleClose(String name, int pos) {
        String key = canon(name);
        int idx = -1;
        for (int k = stack.size() - 1; k >= 0; k--) {
            if (stack.get(k).key.equals(key)) {
                idx = k;
                break;
            }
        }
        if (idx < 0) {
            if (strict) throw error("여는 태그가 없는 닫는 태그: </" + name + ">", pos);
            return resolver.has(name); // 아는 태그면 조용히 버리고, 모르면 글자로 남긴다
        }
        if (strict && idx != stack.size() - 1) {
            throw error("닫는 태그 순서가 어긋났다: </" + name + "> 앞에 <" + stack.get(stack.size() - 1).display + "> 가 열려 있다", pos);
        }
        flushText();
        closeFrames(idx);
        return true;
    }

    private Text finish() {
        flushText();
        if (!stack.isEmpty()) {
            if (strict) throw error("닫히지 않은 태그: <" + stack.get(stack.size() - 1).display + ">", -1);
            closeFrames(0);
        }
        return switch (root.size()) {
            case 0 -> Text.empty();
            case 1 -> root.get(0);
            default -> Text.of(new Content.Literal(""), Style.empty(), root);
        };
    }

    private ArrayList<Text> out() { return stack.isEmpty() ? root : stack.get(stack.size() - 1).out; }

    private void flushText() {
        if (text.length() > 0) {
            out().add(Text.text(text.toString()));
            text.setLength(0);
        }
    }

    /** 스택을 idx 위치까지(그 칸 포함) 위에서부터 닫는다. */
    private void closeFrames(int idx) {
        while (stack.size() > idx) {
            Frame f = stack.remove(stack.size() - 1);
            List<Text> kids = f.out;
            ArrayList<Text> parent = out();
            switch (f.tag) {
                case Tag.Styling s -> {
                    if (!kids.isEmpty()) parent.add(styled(s.style(), kids));
                }
                case Tag.Inserting ins -> {
                    Text v = ins.value();
                    if (!kids.isEmpty()) {
                        List<Text> all = new ArrayList<>(v.children().size() + kids.size());
                        all.addAll(v.children());
                        all.addAll(kids);
                        v = v.children(all);
                    }
                    parent.add(v);
                }
                case Tag.Modifying m -> {
                    if (!kids.isEmpty()) parent.addAll(m.modifier().modify(kids));
                }
                default -> { }
            }
        }
    }

    /** 자식들에 스타일을 입힌 하나. 자식이 하나뿐이면 그 자식의 스타일에 합쳐 마디를 줄인다(보이는 결과는 같다). */
    static Text styled(Style style, List<Text> kids) {
        if (kids.size() == 1) {
            Text c = kids.get(0);
            return c.style(c.style().applyTo(style));
        }
        return Text.of(new Content.Literal(""), style, kids);
    }

    // ------------------------------------------------------------------ 원문 치환 사전 훑기

    /** 태그 인자 안까지 포함해 Placeholder.parsed 값을 원문에 끼워 넣는다. 바뀐 게 없으면 같은 문자열. */
    static String expand(String s, TagResolver r, int depth) {
        if (!r.hasPreProcess() || s.indexOf('<') < 0) return s;
        TagScanner sc = new TagScanner();
        StringBuilder out = null;
        int copied = 0, i = 0, n = s.length();
        while (i < n) {
            char c = s.charAt(i);
            if (c == '\\') {
                i += i + 1 < n && (s.charAt(i + 1) == '<' || s.charAt(i + 1) == '\\') ? 2 : 1;
                continue;
            }
            if (c != '<' || !sc.scan(s, i)) {
                i++;
                continue;
            }
            int end = sc.end;
            if (sc.kind != TagScanner.CLOSE) {
                String name = sc.name(s);
                if (name.equals("pre")) {
                    int close = indexOfIgnoreCase(s, "</pre>", end);
                    i = close < 0 ? n : close + 6;
                    continue;
                }
                String v = r.preProcessValue(name);
                if (v != null) {
                    if (out == null) out = new StringBuilder(n + 32);
                    out.append(s, copied, i).append(v);
                    copied = end;
                } else if (sc.argc > 0 && depth < 8) {
                    String inner = s.substring(i + 1, end - 1);
                    if (inner.indexOf('<') >= 0) {
                        String ex = expand(inner, r, depth + 1);
                        if (ex != inner) {
                            if (out == null) out = new StringBuilder(n + 32);
                            out.append(s, copied, i + 1).append(ex).append('>');
                            copied = end;
                        }
                    }
                }
            }
            i = end;
        }
        if (out == null) return s;
        return out.append(s, copied, n).toString();
    }

    // ------------------------------------------------------------------ 도움

    /** 별칭을 한 이름으로 모은 짝 맞추기 키("!" 부정 접두어도 뗀다). */
    static String canon(String n) {
        if (n.startsWith("!")) n = n.substring(1);
        return switch (n) {
            case "b" -> "bold";
            case "i", "em" -> "italic";
            case "u", "underline" -> "underlined";
            case "st" -> "strikethrough";
            case "obf" -> "obfuscated";
            case "c", "colour" -> "color";
            case "tr", "translate" -> "lang";
            case "tr_or", "translate_or" -> "lang_or";
            case "insertion" -> "insert";
            case "sel" -> "selector";
            case "br" -> "newline";
            case "data" -> "nbt";
            case "grey" -> "gray";
            case "dark_grey" -> "dark_gray";
            default -> n;
        };
    }

    static int indexOfIgnoreCase(String s, String needle, int from) {
        int n = needle.length();
        for (int i = from, max = s.length() - n; i <= max; i++) {
            if (s.regionMatches(true, i, needle, 0, n)) return i;
        }
        return -1;
    }

    private MarkupParseException error(String msg, int pos) { return new MarkupParseException(msg, original, pos); }

    // ------------------------------------------------------------------ Context

    @Override
    public Text deserialize(String markup) { return parseFull(markup, resolver, strict, nest + 1); }

    @Override
    public Text deserialize(String markup, TagResolver... extra) {
        if (extra.length == 0) return deserialize(markup);
        TagResolver[] all = Arrays.copyOf(extra, extra.length + 1);
        all[extra.length] = resolver;
        return parseFull(markup, TagResolver.resolver(all), strict, nest + 1);
    }

    @Override
    public MarkupParseException newException(String message) { return new MarkupParseException(message, original, curPos); }

    @Override
    public MarkupParseException newException(String message, ArgumentQueue args) { return newException(message); }

    @Override
    public String originalMessage() { return original; }
}
