package org.bukkit.util;

/** Bukkit 의 수 변환 도우미. 값이 수가 아니면 문자열로 읽어 보고, 그것도 안 되면 0 이다(Bukkit 과 같은 규칙). */
public final class NumberConversions {

    private NumberConversions() {}

    public static int floor(double num) {
        int f = (int) num;
        return num < f ? f - 1 : f;
    }

    public static int ceil(double num) {
        int f = (int) num;
        return num > f ? f + 1 : f;
    }

    public static int round(double num) { return floor(num + 0.5d); }

    public static double square(double num) { return num * num; }

    public static int toInt(Object object) {
        if (object instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(object));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static float toFloat(Object object) {
        if (object instanceof Number n) return n.floatValue();
        try {
            return Float.parseFloat(String.valueOf(object));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double toDouble(Object object) {
        if (object instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(object));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static long toLong(Object object) {
        if (object instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(object));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static short toShort(Object object) {
        if (object instanceof Number n) return n.shortValue();
        try {
            return Short.parseShort(String.valueOf(object));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static byte toByte(Object object) {
        if (object instanceof Number n) return n.byteValue();
        try {
            return Byte.parseByte(String.valueOf(object));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static boolean isFinite(double d) { return Double.isFinite(d); }

    public static boolean isFinite(float f) { return Float.isFinite(f); }

    public static void checkFinite(double d, String message) {
        if (!isFinite(d)) throw new IllegalArgumentException(message);
    }

    public static void checkFinite(float d, String message) {
        if (!isFinite(d)) throw new IllegalArgumentException(message);
    }
}
