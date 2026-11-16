package org.bukkit.craftbukkit.block.banner;

import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.block.banner.PatternType;

/**
 * Paper CraftPatternType: 무늬 종류 하나. 키마다 한 객체만 만든다({@link org.domi.furnace.compat.paper.BannerPatternRegistry}).
 * 이름은 minecraft 값이면 대문자 경로(옛 열거형 이름), 다른 이름공간이면 키 전체. 차례(ordinal)는 만든 순서.
 */
public final class CraftPatternType implements PatternType {

    private final NamespacedKey key;
    private final String name;
    private final int ordinal;

    public CraftPatternType(NamespacedKey key, int ordinal) {
        this.key = key;
        this.name = NamespacedKey.MINECRAFT.equals(key.getNamespace()) ? key.getKey().toUpperCase(Locale.ROOT) : key.toString();
        this.ordinal = ordinal;
    }

    @Override
    public NamespacedKey getKey() { return key; }

    @Override
    public int compareTo(PatternType other) { return ordinal - other.ordinal(); }

    @Override
    public String name() { return name; }

    @Override
    public int ordinal() { return ordinal; }

    @Override
    public String toString() { return name; }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof CraftPatternType t && key.equals(t.key);
    }

    @Override
    public int hashCode() { return key.hashCode(); }

    /** 1.20.4 까지의 짧은 이름(바닐라 BannerPatternFormatFix 표와 같고, 1.21 의 flow·guster 는 Paper 가 붙인 flw·gus). */
    @Override
    public String getIdentifier() {
        return switch (name) {
            case "BASE" -> "b";
            case "SQUARE_BOTTOM_LEFT" -> "bl";
            case "SQUARE_BOTTOM_RIGHT" -> "br";
            case "SQUARE_TOP_LEFT" -> "tl";
            case "SQUARE_TOP_RIGHT" -> "tr";
            case "STRIPE_BOTTOM" -> "bs";
            case "STRIPE_TOP" -> "ts";
            case "STRIPE_LEFT" -> "ls";
            case "STRIPE_RIGHT" -> "rs";
            case "STRIPE_CENTER" -> "cs";
            case "STRIPE_MIDDLE" -> "ms";
            case "STRIPE_DOWNRIGHT" -> "drs";
            case "STRIPE_DOWNLEFT" -> "dls";
            case "SMALL_STRIPES" -> "ss";
            case "CROSS" -> "cr";
            case "STRAIGHT_CROSS" -> "sc";
            case "TRIANGLE_BOTTOM" -> "bt";
            case "TRIANGLE_TOP" -> "tt";
            case "TRIANGLES_BOTTOM" -> "bts";
            case "TRIANGLES_TOP" -> "tts";
            case "DIAGONAL_LEFT" -> "ld";
            case "DIAGONAL_UP_RIGHT" -> "rd";
            case "DIAGONAL_UP_LEFT" -> "lud";
            case "DIAGONAL_RIGHT" -> "rud";
            case "CIRCLE" -> "mc";
            case "RHOMBUS" -> "mr";
            case "HALF_VERTICAL" -> "vh";
            case "HALF_HORIZONTAL" -> "hh";
            case "HALF_VERTICAL_RIGHT" -> "vhr";
            case "HALF_HORIZONTAL_BOTTOM" -> "hhb";
            case "BORDER" -> "bo";
            case "CURLY_BORDER" -> "cbo";
            case "CREEPER" -> "cre";
            case "GRADIENT" -> "gra";
            case "GRADIENT_UP" -> "gru";
            case "BRICKS" -> "bri";
            case "SKULL" -> "sku";
            case "FLOWER" -> "flo";
            case "MOJANG" -> "moj";
            case "GLOBE" -> "glb";
            case "PIGLIN" -> "pig";
            case "FLOW" -> "flw";
            case "GUSTER" -> "gus";
            default -> key.toString();
        };
    }
}
