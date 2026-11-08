package net.minecraft.network.chat;

import java.util.Locale;
import java.util.Objects;
import net.minecraft.ChatFormatting;

/**
 * 글자 색(Mojang 이름 shim, H7b). 바닐라처럼 {@link #fromRgb} 는 이름 없는 색({@code #RRGGBB} 로 직렬화), {@link #fromLegacyFormat} 은
 * 이름 있는 색이다. equals 는 값만, hashCode 는 (값, 이름) — 바닐라 바이트코드와 같다. parseColor(DataResult)는 serialization 라이브러리가
 * 없어 두지 않았다.
 */
public final class TextColor {

    private final int value;
    private final String name;

    private TextColor(int value, String name) {
        this.value = value & 0xFFFFFF;
        this.name = name;
    }

    public static TextColor fromRgb(int rgb) { return new TextColor(rgb, null); }

    /** 색 서식이 아니면 null. */
    public static TextColor fromLegacyFormat(ChatFormatting f) {
        return f != null && f.isColor() ? new TextColor(f.getColor(), f.getName()) : null;
    }

    public int getValue() { return value; }

    public String serialize() { return name != null ? name : formatValue(); }

    private String formatValue() { return String.format(Locale.ROOT, "#%06X", value); }

    /** 호환 모듈 전용. */
    public org.domi.furnace.api.text.TextColor furnace() {
        if (name != null) {
            var named = org.domi.furnace.api.text.NamedTextColor.byName(name);
            if (named != null) return named;
        }
        return org.domi.furnace.api.text.TextColor.color(value);
    }

    /** 호환 모듈 전용 — 이름 있는 Furnace 색은 이름을 지킨다. */
    public static TextColor fromFurnace(org.domi.furnace.api.text.TextColor c) {
        return c == null ? null : new TextColor(c.value(), c.isNamed() ? c.name() : null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        return o != null && o.getClass() == getClass() && value == ((TextColor) o).value;
    }

    @Override
    public int hashCode() { return Objects.hash(value, name); }

    @Override
    public String toString() { return serialize(); }
}
