// 바닐라 DensityFunctions 비-노이즈 노드 비트정확 포팅 (noise/cache/marker 노드는 Task 9)
package org.domi.furnace.server.world.gen.df;

import org.domi.furnace.server.world.gen.MathHelper;

public final class DensityFunctions {

    private DensityFunctions() {}

    // ================================================================
    // Static factory methods
    // ================================================================

    public static DensityFunction constant(double value) {
        return new Constant(value);
    }

    public static DensityFunction zero() {
        return Constant.ZERO;
    }

    public static DensityFunction add(DensityFunction a, DensityFunction b) {
        return TwoArgFn.create(TwoArgFn.Type.ADD, a, b);
    }

    public static DensityFunction mul(DensityFunction a, DensityFunction b) {
        return TwoArgFn.create(TwoArgFn.Type.MUL, a, b);
    }

    public static DensityFunction min(DensityFunction a, DensityFunction b) {
        return TwoArgFn.create(TwoArgFn.Type.MIN, a, b);
    }

    public static DensityFunction max(DensityFunction a, DensityFunction b) {
        return TwoArgFn.create(TwoArgFn.Type.MAX, a, b);
    }

    public static DensityFunction map(DensityFunction input, Mapped.Type type) {
        return Mapped.create(type, input);
    }

    public static DensityFunction yClampedGradient(int fromY, int toY, double fromValue, double toValue) {
        return new YClampedGradient(fromY, toY, fromValue, toValue);
    }

    public static DensityFunction rangeChoice(
            DensityFunction input,
            double minInclusive,
            double maxExclusive,
            DensityFunction whenInRange,
            DensityFunction whenOutOfRange) {
        return new RangeChoice(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static <C, I extends ToFloatFunction<C>>
    DensityFunction spline(CubicSpline<C, I> spline) {
        return new Spline<>(spline);
    }

    // ================================================================
    // Constant
    // ================================================================

    record Constant(double value) implements DensityFunction.SimpleFunction {
        static final Constant ZERO = new Constant(0.0);

        @Override public double compute(DensityFunction.FunctionContext ctx) { return value; }
        @Override public void fillArray(double[] arr, DensityFunction.ContextProvider p) {
            java.util.Arrays.fill(arr, value);
        }
        @Override public double minValue() { return value; }
        @Override public double maxValue() { return value; }
    }

    // ================================================================
    // TwoArgFn (vanilla: TwoArgumentSimpleFunction / Ap2 / MulOrAdd)
    // ================================================================

    /**
     * Internal interface matching vanilla TwoArgumentSimpleFunction.
     * The create() factory collapses constant arms into MulOrAdd (optimized path),
     * or falls back to Ap2 for general case.
     */
    interface TwoArgFn extends DensityFunction {
        enum Type {
            ADD, MUL, MIN, MAX
        }

        Type type();
        DensityFunction argument1();
        DensityFunction argument2();

        static TwoArgFn create(Type type, DensityFunction a, DensityFunction b) {
            double aMin = a.minValue(), aMax = a.maxValue();
            double bMin = b.minValue(), bMax = b.maxValue();

            // compute min/max for the result
            double resMin = switch (type) {
                case ADD -> aMin + bMin;
                case MUL -> aMin > 0.0 && bMin > 0.0 ? aMin * bMin
                          : aMax < 0.0 && bMax < 0.0 ? aMax * bMax
                          : Math.min(aMin * bMax, aMax * bMin);
                case MIN -> Math.min(aMin, bMin);
                case MAX -> Math.max(aMin, bMin);
            };
            double resMax = switch (type) {
                case ADD -> aMax + bMax;
                case MUL -> aMin > 0.0 && bMin > 0.0 ? aMax * bMax
                          : aMax < 0.0 && bMax < 0.0 ? aMin * bMin
                          : Math.max(aMin * bMin, aMax * bMax);
                case MIN -> Math.min(aMax, bMax);
                case MAX -> Math.max(aMax, bMax);
            };

            // Collapse constant arms for MUL/ADD -> MulOrAdd (vanilla optimization)
            if (type == Type.MUL || type == Type.ADD) {
                if (a instanceof Constant ca) {
                    MulOrAdd.SubType sub = (type == Type.ADD) ? MulOrAdd.SubType.ADD : MulOrAdd.SubType.MUL;
                    return new MulOrAdd(sub, b, resMin, resMax, ca.value());
                }
                if (b instanceof Constant cb) {
                    MulOrAdd.SubType sub = (type == Type.ADD) ? MulOrAdd.SubType.ADD : MulOrAdd.SubType.MUL;
                    return new MulOrAdd(sub, a, resMin, resMax, cb.value());
                }
            }
            return new Ap2(type, a, b, resMin, resMax);
        }
    }

    // ------------------------------------------------------------------
    // Ap2: general two-argument node
    // ------------------------------------------------------------------

    record Ap2(
            TwoArgFn.Type type,
            DensityFunction argument1,
            DensityFunction argument2,
            double minValue,
            double maxValue
    ) implements TwoArgFn {

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            double d = argument1.compute(ctx);
            return switch (type) {
                case ADD -> d + argument2.compute(ctx);
                case MUL -> d == 0.0 ? 0.0 : d * argument2.compute(ctx);
                case MIN -> d < argument2.minValue() ? d : Math.min(d, argument2.compute(ctx));
                case MAX -> d > argument2.maxValue() ? d : Math.max(d, argument2.compute(ctx));
            };
        }

        @Override
        public void fillArray(double[] arr, DensityFunction.ContextProvider p) {
            argument1.fillArray(arr, p);
            switch (type) {
                case ADD -> {
                    double[] tmp = new double[arr.length];
                    argument2.fillArray(tmp, p);
                    for (int i = 0; i < arr.length; i++) arr[i] += tmp[i];
                }
                case MUL -> {
                    for (int i = 0; i < arr.length; i++) {
                        double d = arr[i];
                        arr[i] = d == 0.0 ? 0.0 : d * argument2.compute(p.forIndex(i));
                    }
                }
                case MIN -> {
                    double bMin = argument2.minValue();
                    for (int i = 0; i < arr.length; i++) {
                        double d = arr[i];
                        arr[i] = d < bMin ? d : Math.min(d, argument2.compute(p.forIndex(i)));
                    }
                }
                case MAX -> {
                    double bMax = argument2.maxValue();
                    for (int i = 0; i < arr.length; i++) {
                        double d = arr[i];
                        arr[i] = d > bMax ? d : Math.max(d, argument2.compute(p.forIndex(i)));
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // MulOrAdd: constant-folded TwoArgFn (one arm is a literal constant)
    // Implements both TwoArgFn and PureTransformer.
    // ------------------------------------------------------------------

    record MulOrAdd(
            SubType specificType,
            DensityFunction input,
            double minValue,
            double maxValue,
            double argument
    ) implements TwoArgFn, PureTransformer {

        enum SubType { MUL, ADD }

        @Override public TwoArgFn.Type type() {
            return specificType == SubType.MUL ? TwoArgFn.Type.MUL : TwoArgFn.Type.ADD;
        }
        @Override public DensityFunction argument1() { return constant(argument); }
        @Override public DensityFunction argument2() { return input; }

        @Override public double transform(double v) {
            return specificType == SubType.MUL ? v * argument : v + argument;
        }
    }

    // ================================================================
    // PureTransformer (vanilla interface)
    // ================================================================

    interface PureTransformer extends DensityFunction {
        DensityFunction input();
        double transform(double value);

        @Override
        default double compute(DensityFunction.FunctionContext ctx) {
            return transform(input().compute(ctx));
        }

        @Override
        default void fillArray(double[] arr, DensityFunction.ContextProvider p) {
            input().fillArray(arr, p);
            for (int i = 0; i < arr.length; i++) arr[i] = transform(arr[i]);
        }
    }

    // ================================================================
    // Clamp
    // ================================================================

    record Clamp(DensityFunction input, double minValue, double maxValue) implements PureTransformer {
        @Override public double transform(double v) { return MathHelper.clamp(v, minValue, maxValue); }
    }

    // ================================================================
    // Mapped (abs/square/cube/halfNegative/quarterNegative/squeeze)
    // ================================================================

    record Mapped(Type type, DensityFunction input, double minValue, double maxValue) implements PureTransformer {

        public enum Type {
            ABS, SQUARE, CUBE, HALF_NEGATIVE, QUARTER_NEGATIVE, SQUEEZE
        }

        public static Mapped create(Type type, DensityFunction input) {
            double lo = input.minValue();
            double hi = input.maxValue();
            double tlo = transform(type, lo);
            double thi = transform(type, hi);
            if (type == Type.ABS || type == Type.SQUARE) {
                // For abs/square, min is max(0, min-of-abs/square), max is max of both
                return new Mapped(type, input, Math.max(0.0, lo), Math.max(tlo, thi));
            }
            return new Mapped(type, input, tlo, thi);
        }

        private static double transform(Type type, double v) {
            return switch (type) {
                case ABS -> Math.abs(v);
                case SQUARE -> v * v;
                case CUBE -> v * v * v;
                case HALF_NEGATIVE -> v > 0.0 ? v : v * 0.5;
                case QUARTER_NEGATIVE -> v > 0.0 ? v : v * 0.25;
                case SQUEEZE -> {
                    double d = MathHelper.clamp(v, -1.0, 1.0);
                    yield d / 2.0 - d * d * d / 24.0;
                }
            };
        }

        @Override public double transform(double v) { return transform(type, v); }
    }

    // ================================================================
    // YClampedGradient  — vanilla: Mth.clampedMap(blockY, fromY, toY, fromValue, toValue)
    // ================================================================

    record YClampedGradient(int fromY, int toY, double fromValue, double toValue)
            implements DensityFunction.SimpleFunction {

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            // clampedMap(y, fromY, toY, fromValue, toValue)
            // = clampedLerp(fromValue, toValue, inverseLerp(y, fromY, toY))
            double t = ((double) ctx.blockY() - fromY) / ((double) toY - fromY);
            return MathHelper.clampedLerp(fromValue, toValue, t);
        }

        @Override public double minValue() { return Math.min(fromValue, toValue); }
        @Override public double maxValue() { return Math.max(fromValue, toValue); }
    }

    // ================================================================
    // RangeChoice
    // ================================================================

    record RangeChoice(
            DensityFunction input,
            double minInclusive,
            double maxExclusive,
            DensityFunction whenInRange,
            DensityFunction whenOutOfRange
    ) implements DensityFunction {

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            double d = input.compute(ctx);
            return d >= minInclusive && d < maxExclusive
                    ? whenInRange.compute(ctx)
                    : whenOutOfRange.compute(ctx);
        }

        @Override
        public void fillArray(double[] arr, DensityFunction.ContextProvider p) {
            input.fillArray(arr, p);
            for (int i = 0; i < arr.length; i++) {
                double d = arr[i];
                arr[i] = d >= minInclusive && d < maxExclusive
                        ? whenInRange.compute(p.forIndex(i))
                        : whenOutOfRange.compute(p.forIndex(i));
            }
        }

        @Override public double minValue() { return Math.min(whenInRange.minValue(), whenOutOfRange.minValue()); }
        @Override public double maxValue() { return Math.max(whenInRange.maxValue(), whenOutOfRange.maxValue()); }
    }

    // ================================================================
    // Spline  — wraps a CubicSpline; compute casts to float then back to double
    // ================================================================

    record Spline<C, I extends ToFloatFunction<C>>(CubicSpline<C, I> spline) implements DensityFunction {

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            // spline.apply expects type C; DensityFunctions.Spline.Point wraps FunctionContext
            // For use by Task 10 parser, caller supplies a spline whose coordinate
            // is a ToFloatFunction<DensityFunction.FunctionContext>.
            @SuppressWarnings("unchecked")
            CubicSpline<DensityFunction.FunctionContext, ?> s =
                (CubicSpline<DensityFunction.FunctionContext, ?>) spline;
            return s.apply(ctx);
        }

        @Override
        public void fillArray(double[] arr, DensityFunction.ContextProvider p) {
            p.fillAllDirectly(arr, this);
        }

        @Override public double minValue() { return spline.minValue(); }
        @Override public double maxValue() { return spline.maxValue(); }
    }
}
