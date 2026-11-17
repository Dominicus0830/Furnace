package org.bukkit;

import java.util.HashMap;
import java.util.Map;

/** 염료 색 16가지(바닐라 DyeColor 순서). 색 값은 바닐라 textureDiffuseColor·fireworkColor 와 같다(시험이 서버 표와 견준다). */
public enum DyeColor {
    WHITE(0x0, 0xF, 0xF9FFFE, 0xF0F0F0),
    ORANGE(0x1, 0xE, 0xF9801D, 0xEB8844),
    MAGENTA(0x2, 0xD, 0xC74EBD, 0xC354CD),
    LIGHT_BLUE(0x3, 0xC, 0x3AB3DA, 0x6689D3),
    YELLOW(0x4, 0xB, 0xFED83D, 0xDECF2A),
    LIME(0x5, 0xA, 0x80C71F, 0x41CD34),
    PINK(0x6, 0x9, 0xF38BAA, 0xD88198),
    GRAY(0x7, 0x8, 0x474F52, 0x434343),
    LIGHT_GRAY(0x8, 0x7, 0x9D9D97, 0xABABAB),
    CYAN(0x9, 0x6, 0x169C9C, 0x287697),
    PURPLE(0xA, 0x5, 0x8932B8, 0x7B2FBE),
    BLUE(0xB, 0x4, 0x3C44AA, 0x253192),
    BROWN(0xC, 0x3, 0x835432, 0x51301A),
    GREEN(0xD, 0x2, 0x5E7C16, 0x3B511A),
    RED(0xE, 0x1, 0xB02E26, 0xB3312C),
    BLACK(0xF, 0x0, 0x1D1D21, 0x1E1B1B);

    private static final Map<Byte, DyeColor> BY_WOOL = new HashMap<>();
    private static final Map<Byte, DyeColor> BY_DYE = new HashMap<>();
    private static final Map<Color, DyeColor> BY_COLOR = new HashMap<>();
    private static final Map<Color, DyeColor> BY_FIREWORK = new HashMap<>();

    static {
        for (DyeColor c : values()) {
            BY_WOOL.put(c.woolData, c);
            BY_DYE.put(c.dyeData, c);
            BY_COLOR.put(c.color, c);
            BY_FIREWORK.put(c.firework, c);
        }
    }

    private final byte woolData;
    private final byte dyeData;
    private final Color color;
    private final Color firework;

    DyeColor(int woolData, int dyeData, int color, int firework) {
        this.woolData = (byte) woolData;
        this.dyeData = (byte) dyeData;
        this.color = Color.fromRGB(color);
        this.firework = Color.fromRGB(firework);
    }

    /** @deprecated 옛 양털 데이터 값. */
    @Deprecated
    public byte getWoolData() { return woolData; }

    /** @deprecated 옛 염료 데이터 값. */
    @Deprecated
    public byte getDyeData() { return dyeData; }

    /** 가죽 염색 등에 쓰는 색. */
    public Color getColor() { return color; }

    /** 폭죽 색. */
    public Color getFireworkColor() { return firework; }

    @Deprecated
    public static DyeColor getByWoolData(byte data) { return BY_WOOL.get(data); }

    @Deprecated
    public static DyeColor getByDyeData(byte data) { return BY_DYE.get(data); }

    public static DyeColor getByColor(Color color) { return BY_COLOR.get(color); }

    public static DyeColor getByFireworkColor(Color color) { return BY_FIREWORK.get(color); }

    /** @deprecated 옛 이름 SILVER 도 받는 valueOf. */
    @Deprecated
    public static DyeColor legacyValueOf(String name) { return "SILVER".equals(name) ? LIGHT_GRAY : valueOf(name); }
}
