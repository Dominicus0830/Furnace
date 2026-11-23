package net.minecraft.network.chat;

import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.api.text.TriState;

/**
 * 글자 모양(Mojang 이름 shim, H7b, 불변). 값은 Furnace {@link org.domi.furnace.api.text.Style} 이고 바닐라 이름의 읽기·withXxx·applyFormat·
 * applyTo(부모 위에 얹기) 규칙을 입힌다. 클릭·마우스 올림 이벤트는 {@link ClickEvent}·{@link HoverEvent} 로 Furnace 이벤트에 오간다(NMS shim 5번 묶음).
 */
public final class Style {

    public static final Style EMPTY = new Style(org.domi.furnace.api.text.Style.empty());
    public static final ResourceLocation DEFAULT_FONT = ResourceLocation.withDefaultNamespace("default");

    private final org.domi.furnace.api.text.Style s;

    private Style(org.domi.furnace.api.text.Style s) { this.s = s; }

    /** 호환 모듈 전용. 빈 스타일은 EMPTY(바닐라처럼 isEmpty 가 정체성으로 맞는다). */
    public static Style fromFurnace(org.domi.furnace.api.text.Style s) { return s.isEmpty() ? EMPTY : new Style(s); }

    /** 호환 모듈 전용. */
    public org.domi.furnace.api.text.Style furnace() { return s; }

    private Style with(org.domi.furnace.api.text.Style n) { return n.equals(s) ? this : fromFurnace(n); }

    private Boolean flag(TextDecoration d) {
        TriState t = s.decoration(d);
        return t == TriState.NOT_SET ? null : t == TriState.TRUE;
    }

    private Style flag(TextDecoration d, Boolean v) { return with(s.decoration(d, v == null ? TriState.NOT_SET : v ? TriState.TRUE : TriState.FALSE)); }

    public TextColor getColor() { return TextColor.fromFurnace(s.color()); }

    public boolean isBold() { return Boolean.TRUE.equals(flag(TextDecoration.BOLD)); }

    public boolean isItalic() { return Boolean.TRUE.equals(flag(TextDecoration.ITALIC)); }

    public boolean isStrikethrough() { return Boolean.TRUE.equals(flag(TextDecoration.STRIKETHROUGH)); }

    public boolean isUnderlined() { return Boolean.TRUE.equals(flag(TextDecoration.UNDERLINED)); }

    public boolean isObfuscated() { return Boolean.TRUE.equals(flag(TextDecoration.OBFUSCATED)); }

    public boolean isEmpty() { return this == EMPTY; }

    public String getInsertion() { return s.insertion(); }

    public ResourceLocation getFont() { return s.font() != null ? ResourceLocation.parse(s.font()) : DEFAULT_FONT; }

    public Style withColor(TextColor c) { return with(s.color(c == null ? null : c.furnace())); }

    public Style withColor(ChatFormatting f) { return withColor(f != null ? TextColor.fromLegacyFormat(f) : null); }

    public Style withColor(int rgb) { return withColor(TextColor.fromRgb(rgb)); }

    public Style withBold(Boolean v) { return flag(TextDecoration.BOLD, v); }

    public Style withItalic(Boolean v) { return flag(TextDecoration.ITALIC, v); }

    public Style withUnderlined(Boolean v) { return flag(TextDecoration.UNDERLINED, v); }

    public Style withStrikethrough(Boolean v) { return flag(TextDecoration.STRIKETHROUGH, v); }

    public Style withObfuscated(Boolean v) { return flag(TextDecoration.OBFUSCATED, v); }

    public ClickEvent getClickEvent() { return ClickEvent.fromFurnace(s.clickEvent()); }

    public HoverEvent getHoverEvent() { return HoverEvent.fromFurnace(s.hoverEvent()); }

    public Style withClickEvent(ClickEvent e) { return with(s.clickEvent(e == null ? null : e.furnace())); }

    public Style withHoverEvent(HoverEvent e) { return with(s.hoverEvent(e == null ? null : e.furnace())); }

    public Style withInsertion(String v) { return with(s.insertion(v)); }

    public Style withFont(ResourceLocation f) { return with(s.font(f == null ? null : f.toString())); }

    /** 바닐라 applyFormat: 서식 코드 하나를 얹는다(색은 색을, RESET 은 EMPTY). */
    public Style applyFormat(ChatFormatting f) {
        return switch (f) {
            case OBFUSCATED -> withObfuscated(true);
            case BOLD -> withBold(true);
            case STRIKETHROUGH -> withStrikethrough(true);
            case UNDERLINE -> withUnderlined(true);
            case ITALIC -> withItalic(true);
            case RESET -> EMPTY;
            // 바닐라 applyFormat 은 withColor 와 달리 값이 같아도 새 색(이름 있는 색)을 넣는다 — #FFFFFF 위 WHITE 는 "white"(T-235)
            default -> fromFurnace(s.color(TextColor.fromLegacyFormat(f) == null ? null : TextColor.fromLegacyFormat(f).furnace()));
        };
    }

    /** 바닐라 applyLegacyFormat: 색 코드는 서식을 모두 지우고 색만 남긴다. */
    public Style applyLegacyFormat(ChatFormatting f) {
        return switch (f) {
            case OBFUSCATED, BOLD, STRIKETHROUGH, UNDERLINE, ITALIC -> applyFormat(f);
            case RESET -> EMPTY;
            default -> fromFurnace(org.domi.furnace.api.text.Style.empty().color(TextColor.fromLegacyFormat(f).furnace())
                    .decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.STRIKETHROUGH, false).decoration(TextDecoration.UNDERLINED, false)
                    .decoration(TextDecoration.OBFUSCATED, false)
                    .clickEvent(s.clickEvent()).hoverEvent(s.hoverEvent()).insertion(s.insertion()).font(s.font()));
        };
    }

    /** 바닐라 applyFormats: 차례로 얹되 RESET 을 만나면 곧바로 EMPTY(뒤 서식은 버린다, T-235 대조). */
    public Style applyFormats(ChatFormatting... fs) {
        Style out = this;
        for (ChatFormatting f : fs) {
            if (f == ChatFormatting.RESET) return EMPTY;
            out = out.applyFormat(f);
        }
        // 바닐라는 RESET 이 없으면 늘 새 Style 을 만든다 — 서식이 없어 값이 다 비어도 EMPTY 와 다른 객체라 isEmpty 가 거짓(JSON 이 {"text":""} 꼴)
        return out == EMPTY ? new Style(out.s) : out;
    }

    /** 이 스타일에서 정하지 않은 값은 parent 값으로(바닐라 applyTo). */
    public Style applyTo(Style parent) {
        if (this == EMPTY) return parent;
        if (parent == EMPTY) return this;
        return fromFurnace(s.applyTo(parent.s));
    }

    @Override
    public boolean equals(Object o) { return this == o || o instanceof Style st && s.equals(st.s); }

    @Override
    public int hashCode() { return Objects.hash(s); }

    /** 바닐라 꼴: {color=…,bold,!italic,…,clickEvent=…,hoverEvent=…,insertion=…,font=…} — 정해진 칸만, 불리언은 이름·!이름(T-235 대조). */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean[] first = {true};
        value(sb, first, "color", getColor());
        flagString(sb, first, "bold", flag(TextDecoration.BOLD));
        flagString(sb, first, "italic", flag(TextDecoration.ITALIC));
        flagString(sb, first, "underlined", flag(TextDecoration.UNDERLINED));
        flagString(sb, first, "strikethrough", flag(TextDecoration.STRIKETHROUGH));
        flagString(sb, first, "obfuscated", flag(TextDecoration.OBFUSCATED));
        value(sb, first, "clickEvent", getClickEvent());
        value(sb, first, "hoverEvent", getHoverEvent());
        value(sb, first, "insertion", getInsertion());
        value(sb, first, "font", s.font() != null ? ResourceLocation.parse(s.font()) : null);
        return sb.append('}').toString();
    }

    private static void separator(StringBuilder sb, boolean[] first) {
        if (!first[0]) sb.append(',');
        first[0] = false;
    }

    private static void flagString(StringBuilder sb, boolean[] first, String name, Boolean v) {
        if (v == null) return;
        separator(sb, first);
        if (!v) sb.append('!');
        sb.append(name);
    }

    private static void value(StringBuilder sb, boolean[] first, String name, Object v) {
        if (v == null) return;
        separator(sb, first);
        sb.append(name).append('=').append(v);
    }
}
