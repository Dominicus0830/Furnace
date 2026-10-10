// 글자 꾸밈(바닐라 Style 의 bold·italic·underlined·strikethrough·obfuscated). legacyCode 는 § 서식 코드 글자.
package org.domi.furnace.api.text;

public enum TextDecoration {
    OBFUSCATED("obfuscated", 'k'),
    BOLD("bold", 'l'),
    STRIKETHROUGH("strikethrough", 'm'),
    UNDERLINED("underlined", 'n'),
    ITALIC("italic", 'o');

    private final String key;
    private final char legacyCode;

    TextDecoration(String key, char legacyCode) {
        this.key = key;
        this.legacyCode = legacyCode;
    }

    /** JSON/NBT 필드 이름. */
    public String key() { return key; }

    public char legacyCode() { return legacyCode; }

    /** 이름(대소문자 무시)으로, 없으면 null. MiniMessage 별칭(b, i, u, st, obf, em)도 받는다. */
    public static TextDecoration byName(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "obfuscated", "obf" -> OBFUSCATED;
            case "bold", "b" -> BOLD;
            case "strikethrough", "st" -> STRIKETHROUGH;
            case "underlined", "u" -> UNDERLINED;
            case "italic", "i", "em" -> ITALIC;
            default -> null;
        };
    }
}
