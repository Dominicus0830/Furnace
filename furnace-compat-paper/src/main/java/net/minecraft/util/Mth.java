package net.minecraft.util;

/**
 * 수학 도우미(Mojang 이름 shim, 2026-11-17 NMS shim 점검, 등급 A). 순수 함수라 서버 상태에 닿지 않는다.
 * 식은 바닐라 1.21.1 서버 jar 를 {@code javap -c} 로 읽어 맞췄다(난독화 {@code ayo}) — 특히 {@code floor}·{@code ceil} 의 음수 처리,
 * 정수 {@code clamp} 의 {@code Math.min(Math.max(..))} 순서, {@code sin}/{@code cos} 의 65536 칸 표({@code (float)Math.sin(i·2π/65536)})와 10430.378 배율.
 *
 * <p>일부만 있다: 난수·UUID·색·{@code atan2} 표·{@code fastInvSqrt} 계열·광선-상자 교차 등은 쓰는 플러그인이 보이기 전까지 두지 않았다
 * (없는 멤버를 부르면 링크 오류로 곧바로 드러난다 — 값을 지어내지 않는다).
 */
public final class Mth {

    public static final float PI = 3.1415927F;
    public static final float HALF_PI = 1.5707964F;
    public static final float TWO_PI = 6.2831855F;
    public static final float DEG_TO_RAD = 0.017453292F;
    public static final float RAD_TO_DEG = 57.295776F;
    public static final float EPSILON = 1.0E-5F;
    public static final float SQRT_OF_TWO = sqrt(2.0F);

    private static final float SIN_SCALE = 10430.378F;
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
    }

    private Mth() {}

    public static float sin(float v) { return SIN[(int) (v * SIN_SCALE) & 65535]; }

    public static float cos(float v) { return SIN[(int) (v * SIN_SCALE + 16384.0F) & 65535]; }

    public static float sqrt(float v) { return (float) Math.sqrt(v); }

    public static int floor(float v) {
        int i = (int) v;
        return v < (float) i ? i - 1 : i;
    }

    public static int floor(double v) {
        int i = (int) v;
        return v < (double) i ? i - 1 : i;
    }

    public static long lfloor(double v) {
        long l = (long) v;
        return v < (double) l ? l - 1L : l;
    }

    public static float abs(float v) { return Math.abs(v); }

    public static int abs(int v) { return Math.abs(v); }

    public static int ceil(float v) {
        int i = (int) v;
        return v > (float) i ? i + 1 : i;
    }

    public static int ceil(double v) {
        int i = (int) v;
        return v > (double) i ? i + 1 : i;
    }

    public static int clamp(int value, int min, int max) { return Math.min(Math.max(value, min), max); }

    public static long clamp(long value, long min, long max) { return Math.min(Math.max(value, min), max); }

    public static float clamp(float value, float min, float max) { return value < min ? min : Math.min(value, max); }

    public static double clamp(double value, double min, double max) { return value < min ? min : Math.min(value, max); }

    public static double clampedLerp(double start, double end, double delta) {
        if (delta < 0.0) return start;
        return delta > 1.0 ? end : lerp(delta, start, end);
    }

    public static float clampedLerp(float start, float end, float delta) {
        if (delta < 0.0F) return start;
        return delta > 1.0F ? end : lerp(delta, start, end);
    }

    public static double absMax(double a, double b) {
        if (a < 0.0) a = -a;
        if (b < 0.0) b = -b;
        return Math.max(a, b);
    }

    public static boolean equal(float a, float b) { return Math.abs(b - a) < 1.0E-5F; }

    public static boolean equal(double a, double b) { return Math.abs(b - a) < 9.999999747378752E-6; }

    public static int positiveModulo(int numerator, int denominator) { return Math.floorMod(numerator, denominator); }

    public static float positiveModulo(float numerator, float denominator) { return (numerator % denominator + denominator) % denominator; }

    public static double positiveModulo(double numerator, double denominator) { return (numerator % denominator + denominator) % denominator; }

    public static int wrapDegrees(int degrees) {
        int i = degrees % 360;
        if (i >= 180) i -= 360;
        if (i < -180) i += 360;
        return i;
    }

    public static float wrapDegrees(float degrees) {
        float f = degrees % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    public static double wrapDegrees(double degrees) {
        double d = degrees % 360.0;
        if (d >= 180.0) d -= 360.0;
        if (d < -180.0) d += 360.0;
        return d;
    }

    public static float degreesDifference(float from, float to) { return wrapDegrees(to - from); }

    public static boolean isPowerOfTwo(int value) { return value != 0 && (value & value - 1) == 0; }

    private static final int[] MULTIPLY_DE_BRUIJN_BIT_POSITION = {0, 1, 28, 2, 29, 14, 24, 3, 30, 22, 20, 15, 25, 17, 4, 8, 31, 27, 13, 23, 21, 19, 16, 7, 26, 12, 18, 6, 11, 5, 10, 9};

    /** 바닐라 Mth#smallestEncompassingPowerOfTwo. */
    public static int smallestEncompassingPowerOfTwo(int value) {
        int i = value - 1;
        i |= i >> 1;
        i |= i >> 2;
        i |= i >> 4;
        i |= i >> 8;
        i |= i >> 16;
        return i + 1;
    }

    /** 바닐라 Mth#ceillog2(드브루인 표 — 0 은 0). */
    public static int ceillog2(int value) {
        value = isPowerOfTwo(value) ? value : smallestEncompassingPowerOfTwo(value);
        return MULTIPLY_DE_BRUIJN_BIT_POSITION[(int) ((long) value * 125613361L >> 27) & 31];
    }

    /** 바닐라 Mth#log2. */
    public static int log2(int value) { return ceillog2(value) - (isPowerOfTwo(value) ? 0 : 1); }

    public static float frac(float v) { return v - (float) floor(v); }

    public static double frac(double v) { return v - (double) lfloor(v); }

    public static double inverseLerp(double value, double start, double end) { return (value - start) / (end - start); }

    public static float inverseLerp(float value, float start, float end) { return (value - start) / (end - start); }

    public static float lerp(float delta, float start, float end) { return start + delta * (end - start); }

    public static double lerp(double delta, double start, double end) { return start + delta * (end - start); }

    public static double smoothstep(double x) { return x * x * x * (x * (x * 6.0 - 15.0) + 10.0); }

    public static int sign(double v) {
        if (v == 0.0) return 0;
        return v > 0.0 ? 1 : -1;
    }

    public static float square(float v) { return v * v; }

    public static double square(double v) { return v * v; }

    public static int square(int v) { return v * v; }

    public static long square(long v) { return v * v; }

    public static double clampedMap(double value, double inMin, double inMax, double outMin, double outMax) {
        return clampedLerp(outMin, outMax, inverseLerp(value, inMin, inMax));
    }

    public static float clampedMap(float value, float inMin, float inMax, float outMin, float outMax) {
        return clampedLerp(outMin, outMax, inverseLerp(value, inMin, inMax));
    }

    public static double map(double value, double inMin, double inMax, double outMin, double outMax) {
        return lerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    public static float map(float value, float inMin, float inMax, float outMin, float outMax) {
        return lerp(inverseLerp(value, inMin, inMax), outMin, outMax);
    }

    /** 바닐라 Mth#getSeed: 위치로 만든 씨앗(옛 LCG 위치 난수의 바탕). */
    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }
}
