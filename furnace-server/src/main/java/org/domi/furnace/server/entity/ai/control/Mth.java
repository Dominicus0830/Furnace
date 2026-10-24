// 각도 계산(바닐라 Mth 의 필요한 부분).
package org.domi.furnace.server.entity.ai.control;

public final class Mth {
    private static final double FRAC_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ASIN_TAB = new double[257], COS_TAB = new double[257];

    static {
        for (int i = 0; i < 257; i++) {
            double d = i / 256.0;
            double asin = Math.asin(d);
            COS_TAB[i] = Math.cos(asin);
            ASIN_TAB[i] = asin;
        }
    }

    public static float sin(float v) { return org.domi.furnace.server.block.Dir.sin(v); }

    public static float cos(float v) { return org.domi.furnace.server.block.Dir.cos(v); }

    /** Mth.fastInvSqrt(double). */
    public static double fastInvSqrt(double x) {
        double half = 0.5 * x;
        long bits = Double.doubleToRawLongBits(x);
        bits = 6910469410427058090L - (bits >> 1);
        x = Double.longBitsToDouble(bits);
        return x * (1.5 - half * x * x);
    }

    /** Mth.atan2(y, x): 표를 쓰는 근사(바닐라 그대로). */
    public static double atan2(double y, double x) {
        double d0 = x * x + y * y;
        if (Double.isNaN(d0)) return Double.NaN;
        boolean negY = y < 0.0;
        if (negY) y = -y;
        boolean negX = x < 0.0;
        if (negX) x = -x;
        boolean swap = y > x;
        if (swap) {
            double t = x;
            x = y;
            y = t;
        }
        double inv = fastInvSqrt(d0);
        x *= inv;
        y *= inv;
        double d3 = FRAC_BIAS + y;
        int i = (int) Double.doubleToRawLongBits(d3);
        double d4 = ASIN_TAB[i];
        double d5 = COS_TAB[i];
        double d6 = d3 - FRAC_BIAS;
        double d7 = y * d5 - x * d6;
        double d8 = (6.0 + d7 * d7) * d7 * 0.16666666666666666;
        double r = d4 + d8;
        if (swap) r = (Math.PI / 2) - r;
        if (negX) r = Math.PI - r;
        if (negY) r = -r;
        return r;
    }

    private Mth() { }

    public static float wrapDegrees(float v) {
        float f = v % 360.0F;
        if (f >= 180.0F) f -= 360.0F;
        if (f < -180.0F) f += 360.0F;
        return f;
    }

    public static float degreesDifference(float a, float b) { return wrapDegrees(b - a); }

    /** Mth.rotateIfNecessary(from, to, max): to 에서 최대 max 도 안으로 from 을 끌어온 값. */
    public static float rotateIfNecessary(float from, float to, float max) {
        float diff = degreesDifference(from, to);
        float clamped = Math.clamp(diff, -max, max);
        return to - clamped;
    }

    public static float degreesDifferenceAbs(float a, float b) { return Math.abs(degreesDifference(a, b)); }

    /** Mth.approach(cur, target, delta): target 쪽으로 최대 |delta| 만큼. */
    public static float approach(float cur, float target, float delta) {
        delta = Math.abs(delta);
        return cur < target ? Math.clamp(cur + delta, cur, target) : Math.clamp(cur - delta, target, cur);
    }

    /** Mth.approachDegrees(cur, target, delta): 각도 차이가 작은 쪽으로 최대 delta 도. */
    public static float approachDegrees(float cur, float target, float delta) {
        float diff = degreesDifference(cur, target);
        return approach(cur, cur + diff, delta);
    }

    public static float lerp(float t, float a, float b) { return a + t * (b - a); }
}
