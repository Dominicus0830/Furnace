// 바닐라 net.minecraft.util.Mth 핵심 수학 헬퍼 비트정확 포팅(worldgen 전용)
package org.domi.furnace.server.world.gen;

public final class MathHelper {
    private MathHelper() {}
    public static int floor(double v) { int i = (int) v; return v < i ? i - 1 : i; }
    public static int floor(float v) { int i = (int) v; return v < i ? i - 1 : i; }
    public static double lerp(double t, double a, double b) { return a + t * (b - a); }
    public static double clampedLerp(double a, double b, double t) {
        if (t < 0.0) return a; return t > 1.0 ? b : lerp(t, a, b);
    }
    public static double smoothstep(double v) { return v * v * v * (v * (v * 6.0 - 15.0) + 10.0); }
    public static double clamp(double v, double lo, double hi) { return v < lo ? lo : Math.min(v, hi); }
    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : Math.min(v, hi); }
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }
    public static double square(double v) { return v * v; }
}
