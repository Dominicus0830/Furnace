package net.minecraft.world.item;

import net.minecraft.world.level.material.MapColor;

/**
 * 염료 색 16가지(Mojang 이름 shim, T-212). 번호·이름·색 값은 바닐라 1.21.1 jar 의 값과 같다(천 색은 바닐라처럼 불투명 알파를 붙인다).
 * 코덱({@code CODEC}·{@code STREAM_CODEC})은 두지 않았다.
 */
public enum DyeColor implements net.minecraft.util.StringRepresentable {
    WHITE(0, "white", 0xF9FFFE, MapColor.SNOW, 0xF0F0F0, 0xFFFFFF),
    ORANGE(1, "orange", 0xF9801D, MapColor.COLOR_ORANGE, 0xEB8844, 0xFF681F),
    MAGENTA(2, "magenta", 0xC74EBD, MapColor.COLOR_MAGENTA, 0xC354CD, 0xFF00FF),
    LIGHT_BLUE(3, "light_blue", 0x3AB3DA, MapColor.COLOR_LIGHT_BLUE, 0x6689D3, 0x9AC0CD),
    YELLOW(4, "yellow", 0xFED83D, MapColor.COLOR_YELLOW, 0xDECF2A, 0xFFFF00),
    LIME(5, "lime", 0x80C71F, MapColor.COLOR_LIGHT_GREEN, 0x41CD34, 0xBFFF00),
    PINK(6, "pink", 0xF38BAA, MapColor.COLOR_PINK, 0xD88198, 0xFF69B4),
    GRAY(7, "gray", 0x474F52, MapColor.COLOR_GRAY, 0x434343, 0x808080),
    LIGHT_GRAY(8, "light_gray", 0x9D9D97, MapColor.COLOR_LIGHT_GRAY, 0xABABAB, 0xD3D3D3),
    CYAN(9, "cyan", 0x169C9C, MapColor.COLOR_CYAN, 0x287697, 0x00FFFF),
    PURPLE(10, "purple", 0x8932B8, MapColor.COLOR_PURPLE, 0x7B2FBE, 0xA020F0),
    BLUE(11, "blue", 0x3C44AA, MapColor.COLOR_BLUE, 0x253192, 0x0000FF),
    BROWN(12, "brown", 0x835432, MapColor.COLOR_BROWN, 0x51301A, 0x8B4513),
    GREEN(13, "green", 0x5E7C16, MapColor.COLOR_GREEN, 0x3B511A, 0x00FF00),
    RED(14, "red", 0xB02E26, MapColor.COLOR_RED, 0xB3312C, 0xFF0000),
    BLACK(15, "black", 0x1D1D21, MapColor.COLOR_BLACK, 0x1E1B1B, 0x000000);

    private static final DyeColor[] BY_ID = values();

    private final int id;
    private final String name;
    private final MapColor mapColor;
    private final int textureDiffuseColor;
    private final int fireworkColor;
    private final int textColor;

    DyeColor(int id, String name, int textureDiffuseColor, MapColor mapColor, int fireworkColor, int textColor) {
        this.id = id;
        this.name = name;
        this.mapColor = mapColor;
        this.textColor = textColor;
        this.textureDiffuseColor = 0xFF000000 | textureDiffuseColor;   // 바닐라 FastColor.ARGB32.opaque
        this.fireworkColor = fireworkColor;
    }

    public int getId() { return id; }

    public String getName() { return name; }

    /** 천·양털 색(ARGB, 알파 FF). */
    public int getTextureDiffuseColor() { return textureDiffuseColor; }

    public MapColor getMapColor() { return mapColor; }

    public int getFireworkColor() { return fireworkColor; }

    /** 표지판 글 색(RGB). */
    public int getTextColor() { return textColor; }

    /** 바닐라: 범위 밖 번호는 흰색(ByIdMap ZERO). */
    public static DyeColor byId(int id) { return id >= 0 && id < BY_ID.length ? BY_ID[id] : BY_ID[0]; }

    /** 바닐라: 직렬화 이름(white …)이 맞는 색, 없으면 fallback. */
    public static DyeColor byName(String name, DyeColor fallback) {
        for (DyeColor c : BY_ID) {
            if (c.name.equals(name)) return c;
        }
        return fallback;
    }

    /** 바닐라: 그 폭죽 색인 염료, 없으면 null. */
    public static DyeColor byFireworkColor(int color) {
        for (DyeColor c : BY_ID) {
            if (c.fireworkColor == color) return c;
        }
        return null;
    }

    @Override
    public String toString() { return name; }

    @Override
    public String getSerializedName() { return name; }
}
