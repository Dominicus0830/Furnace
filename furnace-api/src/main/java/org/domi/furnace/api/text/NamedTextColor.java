// 바닐라 16색(ChatFormatting) — 이름·RGB·§ 코드. 이 값과 같은 RGB 는 항상 이 인스턴스로 정규화된다.
package org.domi.furnace.api.text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NamedTextColor {

    private NamedTextColor() {}

    public static final TextColor BLACK = new TextColor(0x000000, "black", '0');
    public static final TextColor DARK_BLUE = new TextColor(0x0000AA, "dark_blue", '1');
    public static final TextColor DARK_GREEN = new TextColor(0x00AA00, "dark_green", '2');
    public static final TextColor DARK_AQUA = new TextColor(0x00AAAA, "dark_aqua", '3');
    public static final TextColor DARK_RED = new TextColor(0xAA0000, "dark_red", '4');
    public static final TextColor DARK_PURPLE = new TextColor(0xAA00AA, "dark_purple", '5');
    public static final TextColor GOLD = new TextColor(0xFFAA00, "gold", '6');
    public static final TextColor GRAY = new TextColor(0xAAAAAA, "gray", '7');
    public static final TextColor DARK_GRAY = new TextColor(0x555555, "dark_gray", '8');
    public static final TextColor BLUE = new TextColor(0x5555FF, "blue", '9');
    public static final TextColor GREEN = new TextColor(0x55FF55, "green", 'a');
    public static final TextColor AQUA = new TextColor(0x55FFFF, "aqua", 'b');
    public static final TextColor RED = new TextColor(0xFF5555, "red", 'c');
    public static final TextColor LIGHT_PURPLE = new TextColor(0xFF55FF, "light_purple", 'd');
    public static final TextColor YELLOW = new TextColor(0xFFFF55, "yellow", 'e');
    public static final TextColor WHITE = new TextColor(0xFFFFFF, "white", 'f');

    /** 코드 순서(0..f). */
    public static final List<TextColor> VALUES = List.of(BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GRAY,
            DARK_GRAY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE);

    private static final Map<String, TextColor> BY_NAME = new HashMap<>();
    private static final Map<Integer, TextColor> BY_VALUE = new HashMap<>();

    static {
        for (TextColor c : VALUES) {
            BY_NAME.put(c.name(), c);
            BY_VALUE.put(c.value(), c);
        }
        BY_NAME.put("grey", GRAY);          // MiniMessage 별칭
        BY_NAME.put("dark_grey", DARK_GRAY);
    }

    static void ensureLoaded() {}

    /** 이름(소문자, grey 별칭 포함)으로, 없으면 null. */
    public static TextColor byName(String name) { return BY_NAME.get(name.toLowerCase(java.util.Locale.ROOT)); }

    static TextColor byValue(int rgb) { return BY_VALUE.get(rgb); }

    /** § 코드 글자로(0-9a-f, 대소문자 무시), 없으면 null. */
    public static TextColor byLegacyCode(char c) {
        int i = Character.digit(c, 16);
        return i < 0 ? null : VALUES.get(i);
    }

    /** 가장 가까운 16색(레거시 출력용, RGB 거리). */
    public static TextColor nearestTo(TextColor color) {
        if (color.isNamed()) return color;
        TextColor best = WHITE;
        long bestD = Long.MAX_VALUE;
        for (TextColor c : VALUES) {
            long dr = c.red() - color.red(), dg = c.green() - color.green(), db = c.blue() - color.blue();
            long d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }
}
