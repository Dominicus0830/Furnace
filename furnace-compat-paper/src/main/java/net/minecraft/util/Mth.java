package net.minecraft.util;

/**
 * 수학 도우미(Mojang 이름 shim, 2026-11-17 NMS shim 점검, 등급 A). 순수 함수라 서버 상태에 닿지 않는다.
 * 식은 바닐라 1.21.1 서버 jar 를 {@code javap -c} 로 읽어 맞췄다(난독화 {@code ayo}) — 특히 {@code floor}·{@code ceil} 의 음수 처리,
 * 정수 {@code clamp} 의 {@code Math.min(Math.max(..))} 순서, {@code sin}/{@code cos} 의 65536 칸 표({@code (float)Math.sin(i·2π/65536)})와 10430.378 배율.
 *
 * <p>2026-11-24(영역 9): 난수 도우미·길이·보간·{@code atan2}·{@code fastInvSqrt} 계열·색·해시 등 순수 계산을 바닐라 {@code javap -c} 로 읽어 더했다.
 * 아직 없는 것: 광선-상자 교차(AABB)·쿼터니언(JOML 의존)·{@code X_AXIS}(JOML)·{@code RANDOM}·인자 없는 {@code createInsecureUUID}·{@code mulAndTruncate}(commons-lang3)·
 * {@code invSqrt}(JOML) 는 껍데기가 맡는다(없는 멤버를 부르면 던진다 — 값을 지어내지 않는다).
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

    private static final double ATAN_BIAS = Double.longBitsToDouble(4805340802404319232L);
    private static final double[] ATAN_ASIN = new double[257];
    private static final double[] ATAN_COS = new double[257];

    static {
        for (int i = 0; i < SIN.length; i++) SIN[i] = (float) Math.sin((double) i * Math.PI * 2.0 / 65536.0);
        for (int i = 0; i < 257; i++) {
            double asin = Math.asin((double) i / 256.0);
            ATAN_COS[i] = Math.cos(asin);
            ATAN_ASIN[i] = asin;
        }
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

    // ---- 2026-11-24 영역 9: 순수 계산(바닐라 javap -c 대로) ----

    public static boolean isMultipleOf(int number, int multiple) { return number % multiple == 0; }

    public static double lengthSquared(double x, double y) { return x * x + y * y; }

    public static double lengthSquared(double x, double y, double z) { return x * x + y * y + z * z; }

    public static float lengthSquared(float x, float y, float z) { return x * x + y * y + z * z; }

    public static double length(double x, double y) { return Math.sqrt(lengthSquared(x, y)); }

    public static double length(double x, double y, double z) { return Math.sqrt(lengthSquared(x, y, z)); }

    public static int floorDiv(int dividend, int divisor) { return Math.floorDiv(dividend, divisor); }

    public static int positiveCeilDiv(int input, int divisor) { return -Math.floorDiv(-input, divisor); }

    public static int roundToward(int input, int multiple) { return positiveCeilDiv(input, multiple) * multiple; }

    public static int lerpInt(float delta, int start, int end) { return start + floor(delta * (float) (end - start)); }

    public static int lerpDiscrete(float delta, int start, int end) {
        int i = end - start;
        return start + floor(delta * (float) (i - 1)) + (delta > 0.0F ? 1 : 0);
    }

    public static float approach(float value, float limit, float stepSize) {
        stepSize = abs(stepSize);
        return value < limit ? clamp(value + stepSize, value, limit) : clamp(value - stepSize, limit, value);
    }

    public static float approachDegrees(float value, float limit, float stepSize) {
        float f = degreesDifference(value, limit);
        return approach(value, value + f, stepSize);
    }

    public static float degreesDifferenceAbs(float from, float to) { return abs(degreesDifference(from, to)); }

    public static float rotLerp(float delta, float start, float end) { return start + delta * wrapDegrees(end - start); }

    public static double rotLerp(double delta, double start, double end) { return start + delta * wrapDegrees(end - start); }

    public static float rotateIfNecessary(float rotating, float target, float maxDifference) {
        float f = degreesDifference(rotating, target);
        float g = clamp(f, -maxDifference, maxDifference);
        return target - g;
    }

    public static float triangleWave(float input, float period) {
        return (Math.abs(input % period - period * 0.5F) - period * 0.25F) / (period * 0.25F);
    }

    public static int murmurHash3Mixer(int input) {
        input ^= input >>> 16;
        input *= -2048144789;
        input ^= input >>> 13;
        input *= -1028477387;
        return input ^ input >>> 16;
    }

    public static long getSeed(int x, int y, int z) {
        long l = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        l = l * l * 42317861L + l * 11L;
        return l >> 16;
    }

    public static long getSeed(net.minecraft.core.Vec3i pos) { return getSeed(pos.getX(), pos.getY(), pos.getZ()); }

    public static int quantize(double value, int factor) { return floor(value / (double) factor) * factor; }

    public static int binarySearch(int min, int max, java.util.function.IntPredicate isTargetBeforeOrAt) {
        int i = max - min;
        while (i > 0) {
            int j = i / 2;
            int k = min + j;
            if (isTargetBeforeOrAt.test(k)) {
                i = j;
            } else {
                min = k + 1;
                i -= j + 1;
            }
        }
        return min;
    }

    /** JOML {@code Math.invsqrt} 와 같은 식. */
    public static float invSqrt(float x) { return 1.0F / (float) Math.sqrt(x); }

    /** JOML {@code Math.invsqrt} 와 같은 식. */
    public static double invSqrt(double x) { return 1.0 / Math.sqrt(x); }

    public static double smoothstepDerivative(double x) { return 30.0 * x * x * (x - 1.0) * (x - 1.0); }

    public static double wobble(double x) {
        return x + (2.0 * RandomSource.create((long) floor(x * 3000.0)).nextDouble() - 1.0) * 1.0E-7 / 2.0;
    }

    public static double lerp2(double delta1, double delta2, double start1, double end1, double start2, double end2) {
        return lerp(delta2, lerp(delta1, start1, end1), lerp(delta1, start2, end2));
    }

    public static float catmullrom(float delta, float controlPoint1, float controlPoint2, float controlPoint3, float controlPoint4) {
        return 0.5F * (2.0F * controlPoint2 + (controlPoint3 - controlPoint1) * delta
            + (2.0F * controlPoint1 - 5.0F * controlPoint2 + 4.0F * controlPoint3 - controlPoint4) * delta * delta
            + (3.0F * controlPoint2 - controlPoint1 - 3.0F * controlPoint3 + controlPoint4) * delta * delta * delta);
    }

    public static int nextInt(RandomSource random, int min, int max) {
        return min >= max ? min : random.nextInt(max - min + 1) + min;
    }

    public static float nextFloat(RandomSource random, float min, float max) {
        return min >= max ? min : random.nextFloat() * (max - min) + min;
    }

    public static double nextDouble(RandomSource random, double min, double max) {
        return min >= max ? min : random.nextDouble() * (max - min) + min;
    }

    public static float randomBetween(RandomSource random, float min, float max) { return random.nextFloat() * (max - min) + min; }

    public static int randomBetweenInclusive(RandomSource random, int min, int max) { return random.nextInt(max - min + 1) + min; }

    public static float normal(RandomSource random, float mean, float deviation) { return mean + (float) random.nextGaussian() * deviation; }

    public static double fastInvSqrt(double x) {
        double d = 0.5 * x;
        long l = Double.doubleToRawLongBits(x);
        l = 6910469410427058090L - (l >> 1);
        x = Double.longBitsToDouble(l);
        return x * (1.5 - d * x * x);
    }

    public static float fastInvCubeRoot(float x) {
        int i = Float.floatToIntBits(x);
        i = 1419967116 - i / 3;
        float f = Float.intBitsToFloat(i);
        f = 0.6666667F * f + 1.0F / (3.0F * f * f * x);
        return 0.6666667F * f + 1.0F / (3.0F * f * f * x);
    }

    public static int getInt(String value, int defaultValue) {
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static int color(float r, float g, float b) { return argb(0, floor(r * 255.0F), floor(g * 255.0F), floor(b * 255.0F)); }

    public static int hsvToRgb(float hue, float saturation, float value) { return hsvToArgb(hue, saturation, value, 0); }

    public static int hsvToArgb(float hue, float saturation, float value, int alpha) {
        int i = (int) (hue * 6.0F) % 6;
        float f = hue * 6.0F - (float) i;
        float g = value * (1.0F - saturation);
        float h = value * (1.0F - f * saturation);
        float j = value * (1.0F - (1.0F - f) * saturation);
        float k;
        float l;
        float m;
        switch (i) {
            case 0 -> { k = value; l = j; m = g; }
            case 1 -> { k = h; l = value; m = g; }
            case 2 -> { k = g; l = value; m = j; }
            case 3 -> { k = g; l = h; m = value; }
            case 4 -> { k = j; l = g; m = value; }
            case 5 -> { k = value; l = g; m = h; }
            default -> throw new RuntimeException("Something went wrong when converting from HSV to RGB. Input was " + hue + ", " + saturation + ", " + value);
        }
        return argb(alpha, clamp((int) (k * 255.0F), 0, 255), clamp((int) (l * 255.0F), 0, 255), clamp((int) (m * 255.0F), 0, 255));
    }

    /** 바닐라 {@code FastColor.ARGB32.color(a, r, g, b)}. */
    private static int argb(int a, int r, int g, int b) { return a << 24 | r << 16 | g << 8 | b; }

    public static double atan2(double y, double x) {
        double d = x * x + y * y;
        if (Double.isNaN(d)) return Double.NaN;
        boolean bl = y < 0.0;
        if (bl) y = -y;
        boolean bl2 = x < 0.0;
        if (bl2) x = -x;
        boolean bl3 = y > x;
        if (bl3) {
            double e = x;
            x = y;
            y = e;
        }
        double e = fastInvSqrt(d);
        x *= e;
        y *= e;
        double f = ATAN_BIAS + y;
        int i = (int) Double.doubleToRawLongBits(f);
        double g = ATAN_ASIN[i];
        double h = ATAN_COS[i];
        double j = f - ATAN_BIAS;
        double k = y * h - x * j;
        double l = (6.0 + k * k) * k * 0.16666666666666666;
        double m = g + l;
        if (bl3) m = 1.5707963267948966 - m;
        if (bl2) m = 3.141592653589793 - m;
        if (bl) m = -m;
        return m;
    }

    public static java.util.UUID createInsecureUUID(RandomSource random) {
        long l = random.nextLong() & -61441L | 16384L;
        long m = random.nextLong() & 4611686018427387903L | Long.MIN_VALUE;
        return new java.util.UUID(l, m);
    }

    public static java.util.stream.IntStream outFromOrigin(int origin, int lowerBound, int upperBound) {
        return outFromOrigin(origin, lowerBound, upperBound, 1);
    }

    public static java.util.stream.IntStream outFromOrigin(int origin, int lowerBound, int upperBound, int steps) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT, "upperbound %d expected to be > lowerBound %d", upperBound, lowerBound));
        }
        if (steps < 1) {
            throw new IllegalArgumentException(String.format(java.util.Locale.ROOT, "steps expected to be >= 1, was %d", steps));
        }
        if (origin < lowerBound || origin > upperBound) return java.util.stream.IntStream.empty();
        return java.util.stream.IntStream.iterate(origin, i -> {
            int j = Math.abs(origin - i);
            return origin - j >= lowerBound || origin + j <= upperBound;
        }, i -> {
            boolean bl = i <= origin;
            int j = Math.abs(origin - i);
            boolean bl2 = origin + j + steps <= upperBound;
            if (!bl || !bl2) {
                int k = origin - j - (bl ? steps : 0);
                if (k >= lowerBound) return k;
            }
            return origin + j + steps;
        });
    }
}
