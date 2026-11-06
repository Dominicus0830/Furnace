// 텍스트 스타일(바닐라 Style) — 색·꾸밈 5종(정하지 않으면 부모 상속)·클릭·호버·삽입 문자열·글꼴. 불변.
package org.domi.furnace.api.text;

import java.util.Objects;

public final class Style {

    private static final Style EMPTY = new Style(null, TriState.NOT_SET, TriState.NOT_SET, TriState.NOT_SET, TriState.NOT_SET,
            TriState.NOT_SET, null, null, null, null);

    private final TextColor color;
    private final TriState bold, italic, underlined, strikethrough, obfuscated;
    private final ClickEvent clickEvent;
    private final HoverEvent hoverEvent;
    private final String insertion;
    private final String font;

    private Style(TextColor color, TriState bold, TriState italic, TriState underlined, TriState strikethrough, TriState obfuscated,
                  ClickEvent clickEvent, HoverEvent hoverEvent, String insertion, String font) {
        this.color = color;
        this.bold = bold;
        this.italic = italic;
        this.underlined = underlined;
        this.strikethrough = strikethrough;
        this.obfuscated = obfuscated;
        this.clickEvent = clickEvent;
        this.hoverEvent = hoverEvent;
        this.insertion = insertion;
        this.font = font;
    }

    public static Style empty() { return EMPTY; }

    public static Style style(TextColor color) { return EMPTY.color(color); }

    public static Style style(TextColor color, TextDecoration... decorations) {
        Style s = EMPTY.color(color);
        for (TextDecoration d : decorations) s = s.decoration(d, TriState.TRUE);
        return s;
    }

    public static Builder builder() { return new Builder(EMPTY); }

    public boolean isEmpty() { return this == EMPTY || equals(EMPTY); }

    public TextColor color() { return color; }

    public TriState decoration(TextDecoration d) {
        return switch (d) {
            case BOLD -> bold;
            case ITALIC -> italic;
            case UNDERLINED -> underlined;
            case STRIKETHROUGH -> strikethrough;
            case OBFUSCATED -> obfuscated;
        };
    }

    public boolean hasDecoration(TextDecoration d) { return decoration(d) == TriState.TRUE; }

    public ClickEvent clickEvent() { return clickEvent; }

    public HoverEvent hoverEvent() { return hoverEvent; }

    public String insertion() { return insertion; }

    /** 글꼴 리소스 키(minecraft:default 등), 없으면 null. */
    public String font() { return font; }

    public Style color(TextColor c) { return new Style(c, bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, insertion, font); }

    public Style decoration(TextDecoration d, boolean v) { return decoration(d, TriState.of(v)); }

    public Style decoration(TextDecoration d, TriState v) {
        Objects.requireNonNull(v);
        return new Style(color,
                d == TextDecoration.BOLD ? v : bold,
                d == TextDecoration.ITALIC ? v : italic,
                d == TextDecoration.UNDERLINED ? v : underlined,
                d == TextDecoration.STRIKETHROUGH ? v : strikethrough,
                d == TextDecoration.OBFUSCATED ? v : obfuscated,
                clickEvent, hoverEvent, insertion, font);
    }

    public Style clickEvent(ClickEvent e) { return new Style(color, bold, italic, underlined, strikethrough, obfuscated, e, hoverEvent, insertion, font); }

    public Style hoverEvent(HoverEvent e) { return new Style(color, bold, italic, underlined, strikethrough, obfuscated, clickEvent, e, insertion, font); }

    public Style insertion(String s) { return new Style(color, bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, s, font); }

    public Style font(String f) { return new Style(color, bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, insertion, f); }

    /**
     * 부모 스타일 위에 이 스타일을 얹은 결과(바닐라 Style.applyTo): 이 스타일에서 정하지 않은 값은 parent 값을 쓴다.
     * 렌더링(레거시·ANSI 출력)·평탄화에 쓴다.
     */
    public Style applyTo(Style parent) {
        if (this.isEmpty()) return parent;
        if (parent.isEmpty()) return this;
        return new Style(color != null ? color : parent.color,
                bold != TriState.NOT_SET ? bold : parent.bold,
                italic != TriState.NOT_SET ? italic : parent.italic,
                underlined != TriState.NOT_SET ? underlined : parent.underlined,
                strikethrough != TriState.NOT_SET ? strikethrough : parent.strikethrough,
                obfuscated != TriState.NOT_SET ? obfuscated : parent.obfuscated,
                clickEvent != null ? clickEvent : parent.clickEvent,
                hoverEvent != null ? hoverEvent : parent.hoverEvent,
                insertion != null ? insertion : parent.insertion,
                font != null ? font : parent.font);
    }

    /** 값이 정해진 항목만 other 로 덮는다(Adventure Style.merge 와 같다). */
    public Style merge(Style other) { return other.applyTo(this); }

    @Override
    public boolean equals(Object o) {
        return o instanceof Style s && Objects.equals(color, s.color) && bold == s.bold && italic == s.italic && underlined == s.underlined
                && strikethrough == s.strikethrough && obfuscated == s.obfuscated && Objects.equals(clickEvent, s.clickEvent)
                && Objects.equals(hoverEvent, s.hoverEvent) && Objects.equals(insertion, s.insertion) && Objects.equals(font, s.font);
    }

    @Override
    public int hashCode() {
        return Objects.hash(color, bold, italic, underlined, strikethrough, obfuscated, clickEvent, hoverEvent, insertion, font);
    }

    /** 플레이 단계 NBT(이름 없는 루트 compound) — 바닐라 Style TRUSTED_STREAM_CODEC 과 같은 바이트. */
    public byte[] networkNbt() { return TextNbt.encodeStyle(this); }

    @Override
    public String toString() {
        return "Style{color=" + color + ", bold=" + bold + ", italic=" + italic + ", underlined=" + underlined + ", strikethrough="
                + strikethrough + ", obfuscated=" + obfuscated + ", click=" + clickEvent + ", hover=" + hoverEvent + ", insertion="
                + insertion + ", font=" + font + "}";
    }

    /** 가변 빌더. */
    public static final class Builder {
        private Style s;

        Builder(Style s) { this.s = s; }

        public Builder color(TextColor c) { s = s.color(c); return this; }

        public Builder decorate(TextDecoration... ds) { for (TextDecoration d : ds) s = s.decoration(d, TriState.TRUE); return this; }

        public Builder decoration(TextDecoration d, boolean v) { s = s.decoration(d, v); return this; }

        public Builder decoration(TextDecoration d, TriState v) { s = s.decoration(d, v); return this; }

        public Builder clickEvent(ClickEvent e) { s = s.clickEvent(e); return this; }

        public Builder hoverEvent(HoverEvent e) { s = s.hoverEvent(e); return this; }

        public Builder insertion(String v) { s = s.insertion(v); return this; }

        public Builder font(String v) { s = s.font(v); return this; }

        public Style build() { return s; }
    }
}
