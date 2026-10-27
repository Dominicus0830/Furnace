// 바닐라 ChatFormatting(팀 색·표시 슬롯 색·color 인자). 선언 순서가 네트워크 번호(VarInt ordinal)다: 색 16개, 서식 5개, reset.
package org.domi.furnace.server.scores;

import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.TextColor;

public enum ChatFormatting {
    BLACK("black", 0, NamedTextColor.BLACK),
    DARK_BLUE("dark_blue", 1, NamedTextColor.DARK_BLUE),
    DARK_GREEN("dark_green", 2, NamedTextColor.DARK_GREEN),
    DARK_AQUA("dark_aqua", 3, NamedTextColor.DARK_AQUA),
    DARK_RED("dark_red", 4, NamedTextColor.DARK_RED),
    DARK_PURPLE("dark_purple", 5, NamedTextColor.DARK_PURPLE),
    GOLD("gold", 6, NamedTextColor.GOLD),
    GRAY("gray", 7, NamedTextColor.GRAY),
    DARK_GRAY("dark_gray", 8, NamedTextColor.DARK_GRAY),
    BLUE("blue", 9, NamedTextColor.BLUE),
    GREEN("green", 10, NamedTextColor.GREEN),
    AQUA("aqua", 11, NamedTextColor.AQUA),
    RED("red", 12, NamedTextColor.RED),
    LIGHT_PURPLE("light_purple", 13, NamedTextColor.LIGHT_PURPLE),
    YELLOW("yellow", 14, NamedTextColor.YELLOW),
    WHITE("white", 15, NamedTextColor.WHITE),
    OBFUSCATED("obfuscated", -1, null),
    BOLD("bold", -1, null),
    STRIKETHROUGH("strikethrough", -1, null),
    UNDERLINE("underline", -1, null),
    ITALIC("italic", -1, null),
    RESET("reset", -1, null);

    private final String name;
    private final int id;
    private final TextColor color;

    ChatFormatting(String name, int id, TextColor color) {
        this.name = name;
        this.id = id;
        this.color = color;
    }

    /** 바닐라 getName. */
    public String key() { return name; }

    /** 바닐라 getId: 색이면 0~15, 아니면 -1. */
    public int id() { return id; }

    public boolean isColor() { return color != null; }

    /** 서식(굵게·기울임 등). reset 은 색도 서식도 아니다. */
    public boolean isFormat() { return color == null && this != RESET; }

    /** 색이면 그 텍스트 색(아니면 null). */
    public TextColor textColor() { return color; }

    /** 바닐라 getByName: 소문자로 바꾸고 영문자만 남겨 비교(없으면 null). */
    public static ChatFormatting byName(String s) {
        if (s == null) return null;
        String k = s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z]", "");
        for (ChatFormatting f : values()) if (f.name.replace("_", "").equals(k)) return f;
        return null;
    }

    /** 바닐라 getById(색 번호 0~15, 그 밖은 null). */
    public static ChatFormatting byColorId(int id) {
        if (id < 0) return null;
        for (ChatFormatting f : values()) if (f.id == id) return f;
        return null;
    }
}
