// Furnace 텍스트(바닐라 Component) — 내용 + 스타일 + 자식. 불변이라 어느 스레드에서나 공유할 수 있고,
// 네트워크 인코딩(NBT)·JSON 을 처음 쓸 때 한 번만 만들어 품는다: 같은 메시지를 여러 명에게 보내도 다시 인코딩하지 않는다.
// 쓰는 법은 Adventure 와 비슷하다: Text.text("안녕", NamedTextColor.RED).append(Text.translatable("key", name)).
package org.domi.furnace.api.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.UnaryOperator;

public final class Text {

    private static final Text EMPTY = new Text(new Content.Literal(""), Style.empty(), List.of());
    private static final Text NEWLINE = new Text(new Content.Literal("\n"), Style.empty(), List.of());
    private static final Text SPACE = new Text(new Content.Literal(" "), Style.empty(), List.of());

    private final Content content;
    private final Style style;
    private final List<Text> children;
    // 인코딩 캐시(불변이므로 경쟁해도 같은 값을 만든다 — 동기화 불필요).
    private volatile byte[] networkNbt;
    private volatile String json;
    private int hash;

    private Text(Content content, Style style, List<Text> children) {
        this.content = Objects.requireNonNull(content, "content");
        this.style = Objects.requireNonNull(style, "style");
        this.children = children;
    }

    // ------------------------------------------------------------------ 만들기

    /** 빈 글자(바닐라 Component.empty) — 자식을 담는 그릇으로 쓴다. */
    public static Text empty() { return EMPTY; }

    public static Text newline() { return NEWLINE; }

    public static Text space() { return SPACE; }

    public static Text text(String content) { return content.isEmpty() ? EMPTY : new Text(new Content.Literal(content), Style.empty(), List.of()); }

    public static Text text(String content, TextColor color) { return new Text(new Content.Literal(content), Style.style(color), List.of()); }

    public static Text text(String content, TextColor color, TextDecoration... decorations) {
        return new Text(new Content.Literal(content), Style.style(color, decorations), List.of());
    }

    public static Text text(String content, Style style) { return new Text(new Content.Literal(content), style, List.of()); }

    /** 숫자·불리언 등 — String.valueOf. */
    public static Text text(Object value) { return text(String.valueOf(value)); }

    /** 번역 텍스트. 인자는 Text 또는 String·Number·Boolean(바닐라처럼 원시값 그대로 보낸다). */
    public static Text translatable(String key, Object... args) {
        return new Text(new Content.Translatable(key, null, Arrays.asList(args)), Style.empty(), List.of());
    }

    public static Text translatable(String key, List<?> args) {
        return new Text(new Content.Translatable(key, null, new ArrayList<>(args)), Style.empty(), List.of());
    }

    /** 클라이언트가 키를 모르면 fallback 을 보여 준다(플러그인 고유 키에 유용). */
    public static Text translatableWithFallback(String key, String fallback, Object... args) {
        return new Text(new Content.Translatable(key, fallback, Arrays.asList(args)), Style.empty(), List.of());
    }

    public static Text keybind(String keybind) { return new Text(new Content.Keybind(keybind), Style.empty(), List.of()); }

    public static Text score(String name, String objective) { return new Text(new Content.Score(name, objective), Style.empty(), List.of()); }

    public static Text selector(String pattern) { return new Text(new Content.Selector(pattern, null), Style.empty(), List.of()); }

    public static Text selector(String pattern, Text separator) { return new Text(new Content.Selector(pattern, separator), Style.empty(), List.of()); }

    public static Text nbt(String path, boolean interpret, Text separator, Content.NbtSource source) {
        return new Text(new Content.Nbt(path, interpret, separator, source), Style.empty(), List.of());
    }

    /** 임의 내용·스타일·자식으로. */
    public static Text of(Content content, Style style, List<Text> children) {
        return new Text(content, style, List.copyOf(children));
    }

    /** 가변 빌더(내용 기본값: 빈 글자). */
    public static Builder builder() { return new Builder(new Content.Literal("")); }

    public static Builder builder(String content) { return new Builder(new Content.Literal(content)); }

    /** 여러 텍스트를 이어 붙인 하나(빈 그릇 + 자식들). */
    public static Text join(Text... parts) { return EMPTY.append(parts); }

    /** separator 를 사이에 끼워 잇는다. */
    public static Text join(Text separator, Iterable<? extends Text> parts) {
        List<Text> out = new ArrayList<>();
        boolean first = true;
        for (Text t : parts) {
            if (!first && separator != null) out.add(separator);
            out.add(t);
            first = false;
        }
        return out.isEmpty() ? EMPTY : new Text(new Content.Literal(""), Style.empty(), List.copyOf(out));
    }

    // ------------------------------------------------------------------ 읽기

    public Content content() { return content; }

    public Style style() { return style; }

    public List<Text> children() { return children; }

    public TextColor color() { return style.color(); }

    public TriState decoration(TextDecoration d) { return style.decoration(d); }

    public boolean hasDecoration(TextDecoration d) { return style.hasDecoration(d); }

    public ClickEvent clickEvent() { return style.clickEvent(); }

    public HoverEvent hoverEvent() { return style.hoverEvent(); }

    public String insertion() { return style.insertion(); }

    public String font() { return style.font(); }

    /** literal 내용이면 그 글자, 아니면 null. */
    public String literal() { return content instanceof Content.Literal l ? l.text() : null; }

    /** 번역 키(번역 텍스트가 아니면 null). */
    public String translationKey() { return content instanceof Content.Translatable t ? t.key() : null; }

    /** 바닐라 tryCollapseToString: 스타일·자식 없는 글자면 그 문자열(직렬화가 문자열 하나로 접는다), 아니면 null. */
    public String collapseToString() {
        return content instanceof Content.Literal l && style.isEmpty() && children.isEmpty() ? l.text() : null;
    }

    // ------------------------------------------------------------------ 바꾼 사본(불변)

    public Text content(Content c) { return new Text(c, style, children); }

    public Text style(Style s) { return s.equals(style) ? this : new Text(content, s, children); }

    public Text style(UnaryOperator<Style> f) { return style(f.apply(style)); }

    /** 스타일을 덮어 합친다(값이 정해진 항목만). */
    public Text mergeStyle(Style s) { return style(style.merge(s)); }

    public Text color(TextColor c) { return style(style.color(c)); }

    /** 색이 없을 때만 칠한다. */
    public Text colorIfAbsent(TextColor c) { return style.color() == null ? color(c) : this; }

    public Text decorate(TextDecoration... ds) {
        Style s = style;
        for (TextDecoration d : ds) s = s.decoration(d, TriState.TRUE);
        return style(s);
    }

    public Text decoration(TextDecoration d, boolean v) { return style(style.decoration(d, v)); }

    public Text decoration(TextDecoration d, TriState v) { return style(style.decoration(d, v)); }

    public Text clickEvent(ClickEvent e) { return style(style.clickEvent(e)); }

    public Text hoverEvent(HoverEvent e) { return style(style.hoverEvent(e)); }

    /** 호버에 텍스트를 보여 준다(hoverEvent(HoverEvent.showText(t)) 줄임). */
    public Text hoverText(Text t) { return hoverEvent(HoverEvent.showText(t)); }

    public Text insertion(String s) { return style(style.insertion(s)); }

    public Text font(String f) { return style(style.font(f)); }

    public Text children(List<Text> c) { return new Text(content, style, List.copyOf(c)); }

    public Text append(Text child) {
        Objects.requireNonNull(child, "child");
        if (child == EMPTY) return this;
        List<Text> c = new ArrayList<>(children.size() + 1);
        c.addAll(children);
        c.add(child);
        return new Text(content, style, Collections.unmodifiableList(c));
    }

    public Text append(Text... more) {
        if (more.length == 0) return this;
        List<Text> c = new ArrayList<>(children.size() + more.length);
        c.addAll(children);
        for (Text t : more) c.add(Objects.requireNonNull(t));
        return new Text(content, style, Collections.unmodifiableList(c));
    }

    public Text append(String literal) { return append(text(literal)); }

    public Text appendNewline() { return append(NEWLINE); }

    public Text appendSpace() { return append(SPACE); }

    // ------------------------------------------------------------------ 출력

    /** 바닐라 Component.getString — 서버 언어로 번역해 이은 평문(색·스타일 없음). */
    public String plainText() {
        StringBuilder sb = new StringBuilder();
        PlainText.append(this, sb, Translators.SERVER_LOCALE);
        return sb.toString();
    }

    /** {@link #plainText()} 와 같다(바닐라 이름). */
    public String getString() { return plainText(); }

    /** 바닐라 JSON 직렬화(Component.Serializer.toJson)와 같은 문자열. 처음 부를 때 한 번 만든다. */
    public String toJson() {
        String j = json;
        if (j == null) json = j = TextJson.encode(this);
        return j;
    }

    /**
     * 플레이 단계 패킷에 들어가는 NBT 인코딩(이름 없는 루트, 1.20.3+). 바닐라 ComponentSerialization + NbtOps 와 같은 바이트다.
     * 처음 부를 때 한 번 만들어 품는다 — 돌려받은 배열을 고치면 안 된다.
     */
    public byte[] networkNbt() {
        byte[] b = networkNbt;
        if (b == null) networkNbt = b = TextNbt.encode(this);
        return b;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o instanceof Text t && content.equals(t.content) && style.equals(t.style) && children.equals(t.children);
    }

    @Override
    public int hashCode() {
        int h = hash;
        if (h == 0) hash = h = Objects.hash(content, style, children);
        return h;
    }

    @Override
    public String toString() { return "Text" + toJson(); }

    // ------------------------------------------------------------------ 빌더

    /** 가변 빌더 — 자식을 많이 붙일 때 사본을 매번 만들지 않는다. */
    public static final class Builder {
        private Content content;
        private Style style = Style.empty();
        private final List<Text> children = new ArrayList<>();

        Builder(Content content) { this.content = content; }

        public Builder content(String literal) { content = new Content.Literal(literal); return this; }

        public Builder content(Content c) { content = c; return this; }

        public Builder style(Style s) { style = s; return this; }

        public Builder color(TextColor c) { style = style.color(c); return this; }

        public Builder decorate(TextDecoration... ds) { for (TextDecoration d : ds) style = style.decoration(d, TriState.TRUE); return this; }

        public Builder decoration(TextDecoration d, boolean v) { style = style.decoration(d, v); return this; }

        public Builder clickEvent(ClickEvent e) { style = style.clickEvent(e); return this; }

        public Builder hoverEvent(HoverEvent e) { style = style.hoverEvent(e); return this; }

        public Builder insertion(String s) { style = style.insertion(s); return this; }

        public Builder font(String f) { style = style.font(f); return this; }

        public Builder append(Text t) { children.add(Objects.requireNonNull(t)); return this; }

        public Builder append(String literal) { return append(text(literal)); }

        public Builder append(Iterable<? extends Text> ts) { for (Text t : ts) append(t); return this; }

        public Text build() { return new Text(content, style, List.copyOf(children)); }
    }
}
