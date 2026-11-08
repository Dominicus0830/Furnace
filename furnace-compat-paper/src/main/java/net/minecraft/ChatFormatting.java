package net.minecraft;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 옛 § 서식 코드(Mojang 이름 shim, H7b, 등급 A). 상수 순서·코드 글자·번호·색 값·toString(§ + 코드)·이름 찾기 규칙은 바닐라 1.21.1 과 같다.
 */
public enum ChatFormatting {
    BLACK('0', 0, 0), DARK_BLUE('1', 1, 170), DARK_GREEN('2', 2, 43520), DARK_AQUA('3', 3, 43690), DARK_RED('4', 4, 11141120),
    DARK_PURPLE('5', 5, 11141290), GOLD('6', 6, 16755200), GRAY('7', 7, 11184810), DARK_GRAY('8', 8, 5592405), BLUE('9', 9, 5592575),
    GREEN('a', 10, 5635925), AQUA('b', 11, 5636095), RED('c', 12, 16733525), LIGHT_PURPLE('d', 13, 16733695), YELLOW('e', 14, 16777045),
    WHITE('f', 15, 16777215),
    OBFUSCATED('k', true), BOLD('l', true), STRIKETHROUGH('m', true), UNDERLINE('n', true), ITALIC('o', true),
    RESET('r', -1, null);

    public static final char PREFIX_CODE = '§';
    private static final Pattern STRIP_FORMATTING_PATTERN = Pattern.compile("(?i)§[0-9A-FK-OR]");

    private final char code;
    private final boolean isFormat;
    private final String toString;
    private final int id;
    private final Integer color;

    ChatFormatting(char code, int id, Integer color) { this(code, false, id, color); }

    ChatFormatting(char code, boolean isFormat) { this(code, isFormat, -1, null); }

    ChatFormatting(char code, boolean isFormat, int id, Integer color) {
        this.code = code;
        this.isFormat = isFormat;
        this.id = id;
        this.color = color;
        this.toString = "§" + code;
    }

    private static String cleanName(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", ""); }


    public char getChar() { return code; }

    public int getId() { return id; }

    public boolean isFormat() { return isFormat; }

    public boolean isColor() { return !isFormat && this != RESET; }

    public Integer getColor() { return color; }

    public String getName() { return name().toLowerCase(Locale.ROOT); }

    public String getSerializedName() { return getName(); }

    @Override
    public String toString() { return toString; }

    /** § 서식 코드를 지운다(null 이면 null). */
    public static String stripFormatting(String s) { return s == null ? null : STRIP_FORMATTING_PATTERN.matcher(s).replaceAll(""); }

    public static ChatFormatting getByName(String name) {
        if (name == null) return null;
        String n = cleanName(name);
        for (ChatFormatting f : values()) if (cleanName(f.name()).equals(n)) return f;
        return null;
    }

    public static ChatFormatting getById(int id) {
        if (id < 0) return RESET;
        for (ChatFormatting f : values()) if (f.getId() == id) return f;
        return null;
    }

    public static ChatFormatting getByCode(char c) {
        char lc = Character.toLowerCase(c);
        for (ChatFormatting f : values()) if (f.code == lc) return f;
        return null;
    }

    public static Collection<String> getNames(boolean colors, boolean formats) {
        List<String> out = new ArrayList<>();
        for (ChatFormatting f : values()) {
            if ((!f.isColor() || colors) && (!f.isFormat() || formats)) out.add(f.getName());
        }
        return out;
    }
}
