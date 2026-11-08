package net.minecraft.network.chat;

import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.api.text.TriState;

/**
 * 글자 모양(Mojang 이름 shim, H7b, 불변). 값은 Furnace {@link org.domi.furnace.api.text.Style} 이고 바닐라 이름의 읽기·withXxx·applyFormat·
 * applyTo(부모 위에 얹기) 규칙을 입힌다. 클릭·마우스 올림 이벤트(ClickEvent·HoverEvent)는 아직 shim 이 없다(없는 멤버 — 호출 위치에서 실패).
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
            default -> withColor(f);
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

    public Style applyFormats(ChatFormatting... fs) {
        Style out = this;
        for (ChatFormatting f : fs) out = out.applyFormat(f);
        return out;
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

    @Override
    public String toString() { return s.toString(); }
}
