package org.bukkit;

import java.util.Locale;
import java.util.regex.Pattern;

/** 채팅 서식 코드(§ + 글자). */
public enum ChatColor {
    BLACK('0'), DARK_BLUE('1'), DARK_GREEN('2'), DARK_AQUA('3'), DARK_RED('4'), DARK_PURPLE('5'), GOLD('6'), GRAY('7'),
    DARK_GRAY('8'), BLUE('9'), GREEN('a'), AQUA('b'), RED('c'), LIGHT_PURPLE('d'), YELLOW('e'), WHITE('f'),
    MAGIC('k'), BOLD('l'), STRIKETHROUGH('m'), UNDERLINE('n'), ITALIC('o'), RESET('r');

    public static final char COLOR_CHAR = '§';
    private static final Pattern STRIP = Pattern.compile("(?i)" + COLOR_CHAR + "[0-9A-FK-ORX]");

    private final char code;
    private final String text;

    ChatColor(char code) {
        this.code = code;
        this.text = new String(new char[]{COLOR_CHAR, code});
    }

    public char getChar() { return code; }

    @Override
    public String toString() { return text; }

    /** 꾸밈(굵게 등)인가 — 색·RESET 은 아니다. */
    public boolean isFormat() { return ordinal() >= MAGIC.ordinal() && this != RESET; }

    public boolean isColor() { return !isFormat() && this != RESET; }

    public static ChatColor getByChar(char code) {
        char c = Character.toLowerCase(code);
        for (ChatColor v : values()) if (v.code == c) return v;
        return null;
    }

    public static ChatColor getByChar(String code) { return code == null || code.isEmpty() ? null : getByChar(code.charAt(0)); }

    /** 서식 코드를 모두 지운다. */
    public static String stripColor(String input) { return input == null ? null : STRIP.matcher(input).replaceAll(""); }

    /** altColorChar(보통 &amp;) 뒤에 서식 글자가 오면 § 로 바꾼다. */
    public static String translateAlternateColorCodes(char altColorChar, String textToTranslate) {
        char[] b = textToTranslate.toCharArray();
        for (int i = 0; i < b.length - 1; i++) {
            if (b[i] == altColorChar && "0123456789AaBbCcDdEeFfKkLlMmNnOoRrXx".indexOf(b[i + 1]) > -1) {
                b[i] = COLOR_CHAR;
                b[i + 1] = Character.toLowerCase(b[i + 1]);
            }
        }
        return new String(b);
    }

    /** 문자열 끝에 이어지는 서식 코드(마지막 색과 그 뒤의 꾸밈). */
    public static String getLastColors(String input) {
        StringBuilder result = new StringBuilder();
        for (int i = input.length() - 2; i >= 0; i--) {
            if (input.charAt(i) != COLOR_CHAR) continue;
            int hex = hexColorStart(input, i);
            if (hex >= 0) {   // 16진 색(§x§r§r§g§g§b§b)은 통째로 — 색이므로 여기서 멈춘다(A15-10)
                result.insert(0, input, hex, i + 2);
                break;
            }
            ChatColor c = getByChar(input.charAt(i + 1));
            if (c == null) continue;
            result.insert(0, c.toString());
            if (c.isColor() || c == RESET) break;
        }
        return result.toString();
    }

    /**
     * i 가 16진 색 코드의 마지막 "§숫자" 자리면 그 코드의 시작(§x 의 §) 위치, 아니면 -1.
     * 16진 색은 §x 뒤에 "§ + 16진 숫자" 여섯 쌍이 이어진 14글자다.
     */
    private static int hexColorStart(String input, int i) {
        int start = i - 12;
        if (start < 0 || i + 1 >= input.length()) return -1;
        if (input.charAt(start) != COLOR_CHAR || input.charAt(start + 1) != 'x') return -1;   // Paper 도 소문자 x 만 본다
        for (int k = start + 2; k <= i; k += 2) {
            if (input.charAt(k) != COLOR_CHAR || !isHexDigit(input.charAt(k + 1))) return -1;
        }
        return start;
    }

    private static boolean isHexDigit(char c) { return c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F'; }

    public String toLowerName() { return name().toLowerCase(Locale.ROOT); }
}
