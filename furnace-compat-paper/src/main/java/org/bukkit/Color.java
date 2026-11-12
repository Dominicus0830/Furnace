package org.bukkit;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * 알파 포함 색(1.20.5 이후 Bukkit 처럼 ARGB). 바꿀 수 없는 값이고 setX 는 새 색을 돌려준다. 각 성분은 0~255(넘으면 IllegalArgumentException).
 */
@SerializableAs("Color")
public final class Color implements ConfigurationSerializable {

    private static final int BIT_MASK = 0xff;
    private static final int DEFAULT_ALPHA = 255;

    public static final Color WHITE = fromRGB(0xFFFFFF);
    public static final Color SILVER = fromRGB(0xC0C0C0);
    public static final Color GRAY = fromRGB(0x808080);
    public static final Color BLACK = fromRGB(0x000000);
    public static final Color RED = fromRGB(0xFF0000);
    public static final Color MAROON = fromRGB(0x800000);
    public static final Color YELLOW = fromRGB(0xFFFF00);
    public static final Color OLIVE = fromRGB(0x808000);
    public static final Color LIME = fromRGB(0x00FF00);
    public static final Color GREEN = fromRGB(0x008000);
    public static final Color AQUA = fromRGB(0x00FFFF);
    public static final Color TEAL = fromRGB(0x008080);
    public static final Color BLUE = fromRGB(0x0000FF);
    public static final Color NAVY = fromRGB(0x000080);
    public static final Color FUCHSIA = fromRGB(0xFF00FF);
    public static final Color PURPLE = fromRGB(0x800080);
    public static final Color ORANGE = fromRGB(0xFFA500);

    private final byte alpha;
    private final byte red;
    private final byte green;
    private final byte blue;

    public static Color fromARGB(int alpha, int red, int green, int blue) { return new Color(alpha, red, green, blue); }

    public static Color fromRGB(int red, int green, int blue) { return new Color(DEFAULT_ALPHA, red, green, blue); }

    public static Color fromBGR(int blue, int green, int red) { return new Color(DEFAULT_ALPHA, red, green, blue); }

    /** 0xRRGGBB. 위 8비트에 값이 있으면 IllegalArgumentException. */
    public static Color fromRGB(int rgb) {
        if ((rgb >> 24) != 0) throw new IllegalArgumentException("Extraneous data in: " + rgb);
        return fromRGB(rgb >> 16 & BIT_MASK, rgb >> 8 & BIT_MASK, rgb & BIT_MASK);
    }

    /** 0xAARRGGBB. */
    public static Color fromARGB(int argb) {
        return fromARGB(argb >> 24 & BIT_MASK, argb >> 16 & BIT_MASK, argb >> 8 & BIT_MASK, argb & BIT_MASK);
    }

    /** 0xBBGGRR. 위 8비트에 값이 있으면 IllegalArgumentException. */
    public static Color fromBGR(int bgr) {
        if ((bgr >> 24) != 0) throw new IllegalArgumentException("Extrenuous data in: " + bgr);   // Bukkit 문구 그대로(오타 포함)
        return fromBGR(bgr >> 16 & BIT_MASK, bgr >> 8 & BIT_MASK, bgr & BIT_MASK);
    }

    private Color(int alpha, int red, int green, int blue) {
        check("Alpha", alpha);
        check("Red", red);
        check("Green", green);
        check("Blue", blue);
        this.alpha = (byte) alpha;
        this.red = (byte) red;
        this.green = (byte) green;
        this.blue = (byte) blue;
    }

    private static void check(String name, int v) {
        if (v < 0 || v > BIT_MASK) throw new IllegalArgumentException(name + "[" + v + "] is not between 0-255");
    }

    public int getAlpha() { return BIT_MASK & alpha; }

    public Color setAlpha(int alpha) { return fromARGB(alpha, getRed(), getGreen(), getBlue()); }

    public int getRed() { return BIT_MASK & red; }

    public Color setRed(int red) { return fromARGB(getAlpha(), red, getGreen(), getBlue()); }

    public int getGreen() { return BIT_MASK & green; }

    public Color setGreen(int green) { return fromARGB(getAlpha(), getRed(), green, getBlue()); }

    public int getBlue() { return BIT_MASK & blue; }

    public Color setBlue(int blue) { return fromARGB(getAlpha(), getRed(), getGreen(), blue); }

    /** 0xRRGGBB(알파 없음). */
    public int asRGB() { return getRed() << 16 | getGreen() << 8 | getBlue(); }

    public int asARGB() { return getAlpha() << 24 | getRed() << 16 | getGreen() << 8 | getBlue(); }

    public int asBGR() { return getBlue() << 16 | getGreen() << 8 | getRed(); }

    /** 이 색과 염료들의 색(가죽 갑옷 염색 식 — 평균에 밝기 보정). */
    public Color mixDyes(DyeColor... colors) {
        if (colors == null || java.util.Arrays.stream(colors).anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("DyeColor cannot be null or contain null values");
        }
        Color[] cs = new Color[colors.length];
        for (int i = 0; i < colors.length; i++) cs[i] = colors[i].getColor();
        return mixColors(cs);
    }

    /** 이 색과 colors 를 섞는다(가죽 갑옷 염색 식). 알파는 255 가 된다. */
    public Color mixColors(Color... colors) {
        if (colors == null || java.util.Arrays.stream(colors).anyMatch(java.util.Objects::isNull)) throw new IllegalArgumentException("Colors cannot be null");
        int totalRed = getRed(), totalGreen = getGreen(), totalBlue = getBlue();
        int totalMax = Math.max(Math.max(totalRed, totalGreen), totalBlue);
        for (Color c : colors) {
            totalRed += c.getRed();
            totalGreen += c.getGreen();
            totalBlue += c.getBlue();
            totalMax += Math.max(Math.max(c.getRed(), c.getGreen()), c.getBlue());
        }
        int n = colors.length + 1;
        // Bukkit 처럼 정수로 나눈 뒤 float 로 — 끝자리 버림까지 같아야 같은 색이 나온다
        float averageRed = totalRed / n, averageGreen = totalGreen / n, averageBlue = totalBlue / n;
        float averageMax = totalMax / n;
        float maximumOfAverages = Math.max(Math.max(averageRed, averageGreen), averageBlue);
        float gainFactor = averageMax / maximumOfAverages;
        return fromRGB((int) (averageRed * gainFactor), (int) (averageGreen * gainFactor), (int) (averageBlue * gainFactor));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Color c && c.alpha == alpha && c.red == red && c.green == green && c.blue == blue;
    }

    @Override
    public int hashCode() { return asARGB() ^ Color.class.hashCode(); }

    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ALPHA", getAlpha());
        m.put("RED", getRed());
        m.put("BLUE", getBlue());
        m.put("GREEN", getGreen());
        return m;
    }

    /** serialize() 의 맵에서(ALPHA 가 없으면 255). */
    public static Color deserialize(Map<String, Object> map) {
        return fromARGB(asInt("ALPHA", map, DEFAULT_ALPHA), asInt("RED", map, null), asInt("GREEN", map, null), asInt("BLUE", map, null));
    }

    private static int asInt(String name, Map<String, Object> map, Integer def) {
        Object v = map.get(name);
        if (v == null) {
            if (def != null) return def;
            throw new IllegalArgumentException(name + " not in map " + map);
        }
        if (!(v instanceof Number n)) throw new IllegalArgumentException(name + "(" + v + ") is not a number");
        return n.intValue();
    }

    @Override
    public String toString() { return "Color:[argb0x" + Integer.toHexString(asARGB()).toUpperCase(java.util.Locale.ROOT) + "]"; }
}
