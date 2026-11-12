// 글자 색 — RGB 값 하나. 바닐라 16색은 이름을 가진 인스턴스(NamedTextColor)로 직렬화 때 이름이 쓰인다.
package org.domi.furnace.api.text;

public final class TextColor {

    private final int value;
    private final String name;     // 바닐라 이름 있는 색이면 이름, 아니면 null
    private final char legacyCode; // § 서식 코드(이름 있는 색만), 없으면 0

    TextColor(int value, String name, char legacyCode) {
        this.value = value & 0xFFFFFF;
        this.name = name;
        this.legacyCode = legacyCode;
    }

    /**
     * RGB(0xRRGGBB). 바닐라 16색과 값이 같으면 그 이름 있는 색을 돌려준다(Adventure TextColor.color 와 같다).
     * 바닐라 TextColor.fromRgb 는 이름을 붙이지 않는다 — 그쪽과 같은 직렬화("#FF5555")가 필요하면 {@link #rgb(int)}.
     */
    public static TextColor color(int rgb) {
        NamedTextColor.ensureLoaded();
        TextColor named = NamedTextColor.byValue(rgb & 0xFFFFFF);
        return named != null ? named : new TextColor(rgb, null, (char) 0);
    }

    /**
     * 이름 없는 RGB 색(바닐라 TextColor.fromRgb 와 같다): 16색과 값이 같아도 직렬화는 "#RRGGBB" 다. {@link #equals} 는 값만 보므로 같은 값의 이름 있는 색과 같다.
     */
    public static TextColor rgb(int rgb) { return new TextColor(rgb, null, (char) 0); }

    public static TextColor color(int r, int g, int b) {
        return color((r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
    }

    /** "#RRGGBB" 를 읽는다. 형식이 틀리면 null. */
    public static TextColor fromHexString(String s) {
        if (s == null || s.length() != 7 || s.charAt(0) != '#') return null;
        int v = 0;
        for (int i = 1; i < 7; i++) {
            int d = Character.digit(s.charAt(i), 16);
            if (d < 0) return null;
            v = v << 4 | d;
        }
        return color(v);
    }

    /** 바닐라 TextColor.parseColor: "#RRGGBB" 또는 16색 이름. 아니면 null. */
    public static TextColor parse(String s) {
        if (s == null) return null;
        if (s.startsWith("#")) return fromHexString(s);
        return NamedTextColor.byName(s);
    }

    public int value() { return value; }

    public int red() { return value >> 16 & 0xFF; }

    public int green() { return value >> 8 & 0xFF; }

    public int blue() { return value & 0xFF; }

    /** 바닐라 16색 이름(아니면 null). */
    public String name() { return name; }

    public boolean isNamed() { return name != null; }

    char legacyCode() { return legacyCode; }

    /** "#RRGGBB"(대문자, 바닐라 formatValue). */
    public String asHexString() { return String.format("#%06X", value); }

    /** 직렬화 값 — 이름 있는 색은 이름, 아니면 "#RRGGBB"(바닐라 TextColor.serialize). */
    public String serialize() { return name != null ? name : asHexString(); }

    /** 두 색 사이 보간(t 0..1) — 그라디언트용. */
    public static TextColor lerp(float t, TextColor a, TextColor b) {
        float c = Math.min(1f, Math.max(0f, t));
        int r = Math.round(a.red() + c * (b.red() - a.red()));
        int g = Math.round(a.green() + c * (b.green() - a.green()));
        int bl = Math.round(a.blue() + c * (b.blue() - a.blue()));
        return color(r, g, bl);
    }

    @Override
    public boolean equals(Object o) { return o instanceof TextColor c && c.value == value; }

    @Override
    public int hashCode() { return value; }

    @Override
    public String toString() { return serialize(); }
}
