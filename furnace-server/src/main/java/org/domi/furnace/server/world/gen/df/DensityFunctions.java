// 바닐라 DensityFunctions 노드 비트정확 포팅 (비-노이즈 + 노이즈/캐시/마커 노드)
package org.domi.furnace.server.world.gen.df;

import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.noise.BlendedNoise;
import org.domi.furnace.server.world.gen.noise.NormalNoise;

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

    // ── Task 9: noise/cache/marker 노드 팩토리 메서드 ──────────────────────────

    public static DensityFunction noise(NormalNoise noise, double xzScale, double yScale) {
        return new Noise(noise, xzScale, yScale);
    }

    public static DensityFunction shiftedNoise(
            DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ,
            double xzScale, double yScale, NormalNoise noise) {
        return new ShiftedNoise(shiftX, shiftY, shiftZ, xzScale, yScale, noise);
    }

    public static DensityFunction shiftA(NormalNoise noise) { return new ShiftA(noise); }
    public static DensityFunction shiftB(NormalNoise noise) { return new ShiftB(noise); }
    public static DensityFunction shift(NormalNoise noise)  { return new Shift(noise); }

    public static DensityFunction weirdScaledSampler(
            DensityFunction input, NormalNoise noise, WeirdScaledSampler.RarityValueMapper mapper) {
        return new WeirdScaledSampler(input, noise, mapper);
    }

    public static DensityFunction oldBlendedNoise(BlendedNoise blendedNoise) {
        return new OldBlendedNoise(blendedNoise);
    }

    // marker/cache 노드
    public static DensityFunction interpolated(DensityFunction wrapped) {
        return new Marker(Marker.Type.Interpolated, wrapped);
    }
    public static DensityFunction flatCache(DensityFunction wrapped) {
        return new Marker(Marker.Type.FlatCache, wrapped);
    }
    public static DensityFunction cache2d(DensityFunction wrapped) {
        return new Marker(Marker.Type.Cache2D, wrapped);
    }
    public static DensityFunction cacheOnce(DensityFunction wrapped) {
        return new Marker(Marker.Type.CacheOnce, wrapped);
    }
    public static DensityFunction cacheAllInCell(DensityFunction wrapped) {
        return new Marker(Marker.Type.CacheAllInCell, wrapped);
    }

    // blend 노드
    public static DensityFunction blendAlpha()  { return BlendAlpha.INSTANCE;  }
    public static DensityFunction blendOffset() { return BlendOffset.INSTANCE; }
    public static DensityFunction blendDensity(DensityFunction input) { return new BlendDensity(input); }

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

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(TwoArgFn.create(type,
                argument1.mapAll(visitor), argument2.mapAll(visitor)));
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

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(TwoArgFn.create(type(), constant(argument), input.mapAll(visitor)));
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

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new Clamp(input.mapAll(visitor), minValue, maxValue));
        }
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

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(Mapped.create(type, input.mapAll(visitor)));
        }
    }

    // ================================================================
    // YClampedGradient  — vanilla: Mth.clampedMap(blockY, fromY, toY, fromValue, toValue)
    // ================================================================

    /** 바닐라 EndIslandDensityFunction — LegacyRandom(seed) 를 17292 회 소비한 SimplexNoise 로 섬 높이를 만든다. */
    static final class EndIslands implements DensityFunction.SimpleFunction {
        private final org.domi.furnace.server.world.gen.noise.SimplexNoise islandNoise;

        EndIslands(long seed) {
            org.domi.furnace.server.world.gen.carver.LegacyRandom r = new org.domi.furnace.server.world.gen.carver.LegacyRandom(seed);
            r.consumeCount(17292);
            this.islandNoise = new org.domi.furnace.server.world.gen.noise.SimplexNoise(r);
        }

        static float heightValue(org.domi.furnace.server.world.gen.noise.SimplexNoise noise, int x, int z) {
            int i = x / 2, j = z / 2, k = x % 2, l = z % 2;
            float f = 100.0F - (float) Math.sqrt((float) (x * x + z * z)) * 8.0F;
            f = Math.max(-100.0F, Math.min(80.0F, f));
            for (int i1 = -12; i1 <= 12; i1++) {
                for (int j1 = -12; j1 <= 12; j1++) {
                    long k1 = (long) (i + i1), l1 = (long) (j + j1);
                    if (k1 * k1 + l1 * l1 > 4096L && noise.getValue((double) k1, (double) l1) < (double) -0.9F) {
                        float f1 = (Math.abs((float) k1) * 3439.0F + Math.abs((float) l1) * 147.0F) % 13.0F + 9.0F;
                        float f2 = (float) (k - i1 * 2), f3 = (float) (l - j1 * 2);
                        float f4 = 100.0F - (float) Math.sqrt(f2 * f2 + f3 * f3) * f1;
                        f4 = Math.max(-100.0F, Math.min(80.0F, f4));
                        f = Math.max(f, f4);
                    }
                }
            }
            return f;
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            return ((double) heightValue(islandNoise, ctx.blockX() / 8, ctx.blockZ() / 8) - 8.0) / 128.0;
        }

        @Override public double minValue() { return -0.84375; }
        @Override public double maxValue() { return 0.5625; }
    }

    public static DensityFunction endIslands(long seed) { return new EndIslands(seed); }

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

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new RangeChoice(
                input.mapAll(visitor), minInclusive, maxExclusive,
                whenInRange.mapAll(visitor), whenOutOfRange.mapAll(visitor)));
        }
    }

    // ================================================================
    // Noise  (minecraft:noise)
    // ================================================================

    record Noise(NormalNoise noise, double xzScale, double yScale) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            return noise.getValue(ctx.blockX() * xzScale, ctx.blockY() * yScale, ctx.blockZ() * xzScale);
        }
        @Override public void fillArray(double[] arr, DensityFunction.ContextProvider p) { p.fillAllDirectly(arr, this); }
        @Override public double minValue() { return -noise.maxValue(); }
        @Override public double maxValue() { return  noise.maxValue(); }
    }

    // ================================================================
    // ShiftedNoise  (minecraft:shifted_noise)
    // ================================================================

    record ShiftedNoise(
            DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ,
            double xzScale, double yScale, NormalNoise noise
    ) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            double d  = ctx.blockX() * xzScale + shiftX.compute(ctx);
            double d1 = ctx.blockY() * yScale   + shiftY.compute(ctx);
            double d2 = ctx.blockZ() * xzScale  + shiftZ.compute(ctx);
            return noise.getValue(d, d1, d2);
        }
        @Override public void fillArray(double[] arr, DensityFunction.ContextProvider p) { p.fillAllDirectly(arr, this); }
        @Override public double minValue() { return -noise.maxValue(); }
        @Override public double maxValue() { return  noise.maxValue(); }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new ShiftedNoise(
                shiftX.mapAll(visitor), shiftY.mapAll(visitor), shiftZ.mapAll(visitor),
                xzScale, yScale, noise));
        }
    }

    // ================================================================
    // ShiftNoise 공통 인터페이스 + Shift / ShiftA / ShiftB
    // 공식: getValue(x*0.25, y*0.25, z*0.25) * 4.0
    // Shift   : compute(x, y, z)
    // ShiftA  : compute(x, 0, z)
    // ShiftB  : compute(z, x, 0)
    // ================================================================

    interface ShiftNoise extends DensityFunction {
        NormalNoise offsetNoise();

        @Override default double minValue() { return -maxValue(); }
        @Override default double maxValue() { return offsetNoise().maxValue() * 4.0; }

        @Override default void fillArray(double[] arr, DensityFunction.ContextProvider p) {
            p.fillAllDirectly(arr, this);
        }

        default double shiftValue(double x, double y, double z) {
            return offsetNoise().getValue(x * 0.25, y * 0.25, z * 0.25) * 4.0;
        }
    }

    record Shift(NormalNoise offsetNoise) implements ShiftNoise {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            return shiftValue(ctx.blockX(), ctx.blockY(), ctx.blockZ());
        }
    }

    record ShiftA(NormalNoise offsetNoise) implements ShiftNoise {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            return shiftValue(ctx.blockX(), 0.0, ctx.blockZ());
        }
    }

    record ShiftB(NormalNoise offsetNoise) implements ShiftNoise {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            return shiftValue(ctx.blockZ(), ctx.blockX(), 0.0);
        }
    }

    // ================================================================
    // WeirdScaledSampler  (minecraft:weird_scaled_sampler)
    // transform(ctx, value) = rarity * |noise.getValue(x/r, y/r, z/r)|
    // ================================================================

    record WeirdScaledSampler(
            DensityFunction input, NormalNoise noise, WeirdScaledSampler.RarityValueMapper rarityValueMapper
    ) implements DensityFunction {

        public enum RarityValueMapper {
            TYPE1 {
                @Override public double map(double value) {
                    // NoiseRouterData.QuantizedSpaghettiRarity.getSpaghettiRarity3D
                    if (value < -0.5) return 0.75;
                    else if (value < 0.0) return 1.0;
                    else if (value < 0.5) return 1.5;
                    else return 2.0;
                }
                @Override public double maxRarity() { return 2.0; }
            },
            TYPE2 {
                @Override public double map(double value) {
                    // NoiseRouterData.QuantizedSpaghettiRarity.getSphaghettiRarity2D
                    if (value < -0.75) return 0.5;
                    else if (value < -0.5) return 0.75;
                    else if (value < 0.5) return 1.0;
                    else if (value < 0.75) return 2.0;
                    else return 3.0;
                }
                @Override public double maxRarity() { return 3.0; }
            };

            public abstract double map(double value);
            public abstract double maxRarity();
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            double value = input.compute(ctx);
            double rarity = rarityValueMapper.map(value);
            return rarity * Math.abs(noise.getValue(
                    ctx.blockX() / rarity, ctx.blockY() / rarity, ctx.blockZ() / rarity));
        }

        @Override public void fillArray(double[] arr, DensityFunction.ContextProvider p) { p.fillAllDirectly(arr, this); }
        @Override public double minValue() { return 0.0; }
        @Override public double maxValue() { return rarityValueMapper.maxRarity() * noise.maxValue(); }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new WeirdScaledSampler(input.mapAll(visitor), noise, rarityValueMapper));
        }
    }

    // ================================================================
    // OldBlendedNoise  (minecraft:old_blended_noise)
    // ================================================================

    record OldBlendedNoise(BlendedNoise blendedNoise) implements DensityFunction.SimpleFunction {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) {
            return blendedNoise.calculate(ctx.blockX(), ctx.blockY(), ctx.blockZ());
        }
        @Override public double minValue() { return -blendedNoise.maxValue(); }
        @Override public double maxValue() { return  blendedNoise.maxValue(); }
    }

    // ================================================================
    // Marker / MarkerOrMarked  (interpolated, flat_cache, cache_2d, cache_once, cache_all_in_cell)
    // Task 12 의 NoiseChunk 가 특별 처리하기 전까지 패스스루로 동작.
    // ================================================================

    public interface MarkerOrMarked extends DensityFunction {
        Marker.Type type();
        DensityFunction wrapped();
    }

    public record Marker(Marker.Type type, DensityFunction wrapped) implements MarkerOrMarked {

        public enum Type {
            Interpolated, FlatCache, Cache2D, CacheOnce, CacheAllInCell
        }

        @Override
        public double compute(DensityFunction.FunctionContext ctx) { return wrapped.compute(ctx); }
        @Override
        public void fillArray(double[] arr, DensityFunction.ContextProvider p) { wrapped.fillArray(arr, p); }
        @Override public double minValue() { return wrapped.minValue(); }
        @Override public double maxValue() { return wrapped.maxValue(); }

        /**
         * 자식을 먼저 재귀 변환한 새 Marker 를 visitor 에 전달한다.
         * NoiseChunk.wrap() 은 여기서 Marker 를 셀 인식 구현체로 교체한다.
         */
        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new Marker(type, wrapped.mapAll(visitor)));
        }
    }

    // ================================================================
    // BlendAlpha / BlendOffset / BlendDensity
    // 새 월드 생성(no blending): alpha=1.0, offset=0.0, density=pass-through
    // ================================================================

    enum BlendAlpha implements DensityFunction.SimpleFunction {
        INSTANCE;
        @Override public double compute(DensityFunction.FunctionContext ctx) { return 1.0; }
        @Override public void fillArray(double[] arr, DensityFunction.ContextProvider p) { java.util.Arrays.fill(arr, 1.0); }
        @Override public double minValue() { return 1.0; }
        @Override public double maxValue() { return 1.0; }
    }

    enum BlendOffset implements DensityFunction.SimpleFunction {
        INSTANCE;
        @Override public double compute(DensityFunction.FunctionContext ctx) { return 0.0; }
        @Override public void fillArray(double[] arr, DensityFunction.ContextProvider p) { java.util.Arrays.fill(arr, 0.0); }
        @Override public double minValue() { return 0.0; }
        @Override public double maxValue() { return 0.0; }
    }

    // BlendDensity: Blender.empty() 경로에서 input 그대로 반환 (pass-through)
    record BlendDensity(DensityFunction input) implements DensityFunction {
        @Override
        public double compute(DensityFunction.FunctionContext ctx) { return input.compute(ctx); }
        @Override
        public void fillArray(double[] arr, DensityFunction.ContextProvider p) { input.fillArray(arr, p); }
        @Override public double minValue() { return Double.NEGATIVE_INFINITY; }
        @Override public double maxValue() { return Double.POSITIVE_INFINITY; }

        @Override
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new BlendDensity(input.mapAll(visitor)));
        }
    }

    // ================================================================
    // Spline  — wraps a CubicSpline; compute casts to float then back to double
    // ================================================================

    // ================================================================
    // cellBounds: 셀 내부 df 값의 보수적 [lo, hi] 구간 전파 (NoiseChunk 셀 skip 용)
    // ================================================================

    /** cellBounds 가 모르는 노드(NoiseChunk 래퍼 등)의 구간을 제공. null 반환 시 글로벌 min/max 폴백. */
    public interface CellBoundsOracle {
        double[] boundsOf(DensityFunction df);
    }

    /** cellBounds 가 모르는 노드(NoiseChunk 래퍼 등)의 구간을 out[0]=lo,out[1]=hi 로 채운다. */
    public interface CellBoundsOracleInto {
        boolean boundsOf(DensityFunction df, double[] out);
    }

    /**
     * 현재 셀 범위에서 df 가 가질 수 있는 값의 보수적 구간 [lo, hi] 를 반환한다.
     * 항상 실제 범위를 포함(⊇)하므로 lo > 0 ⇒ 셀 전체 양수, hi < 0 ⇒ 셀 전체 음수가 보장된다.
     *
     * @param yMin 셀 최저 블록 Y (YClampedGradient 평가용)
     * @param yMax 셀 최고 블록 Y
     */
    public static double[] cellBounds(DensityFunction df, CellBoundsOracle oracle, int yMin, int yMax) {
        double[] out = new double[2];
        cellBounds(df, (node, bounds) -> {
            double[] value = oracle.boundsOf(node);
            if (value == null) return false;
            bounds[0] = value[0];
            bounds[1] = value[1];
            return true;
        }, yMin, yMax, out);
        return out;
    }

    public static void cellBounds(DensityFunction df, CellBoundsOracleInto oracle, int yMin, int yMax, double[] out) {
        if (oracle.boundsOf(df, out)) return;

        if (df instanceof Constant c) {
            out[0] = c.value();
            out[1] = c.value();
            return;
        }
        if (df instanceof TwoArgFn t) {
            double[] a = new double[2];
            double[] b = new double[2];
            cellBounds(t.argument1(), oracle, yMin, yMax, a);
            cellBounds(t.argument2(), oracle, yMin, yMax, b);
            switch (t.type()) {
                case ADD -> {
                    out[0] = a[0] + b[0];
                    out[1] = a[1] + b[1];
                }
                case MIN -> {
                    out[0] = Math.min(a[0], b[0]);
                    out[1] = Math.min(a[1], b[1]);
                }
                case MAX -> {
                    out[0] = Math.max(a[0], b[0]);
                    out[1] = Math.max(a[1], b[1]);
                }
                case MUL -> {
                    double p1 = a[0] * b[0], p2 = a[0] * b[1], p3 = a[1] * b[0], p4 = a[1] * b[1];
                    out[0] = Math.min(Math.min(p1, p2), Math.min(p3, p4));
                    out[1] = Math.max(Math.max(p1, p2), Math.max(p3, p4));
                }
            }
            return;
        }
        if (df instanceof Clamp cl) {
            cellBounds(cl.input(), oracle, yMin, yMax, out);
            out[0] = MathHelper.clamp(out[0], cl.minValue(), cl.maxValue());
            out[1] = MathHelper.clamp(out[1], cl.minValue(), cl.maxValue());
            return;
        }
        if (df instanceof Mapped m) {
            cellBounds(m.input(), oracle, yMin, yMax, out);
            double inLo = out[0];
            double inHi = out[1];
            double tlo = m.transform(inLo);
            double thi = m.transform(inHi);
            if (m.type() == Mapped.Type.ABS || m.type() == Mapped.Type.SQUARE) {
                if (inLo <= 0.0 && inHi >= 0.0) {
                    out[0] = 0.0;
                    out[1] = Math.max(tlo, thi);
                    return;
                }
                out[0] = Math.min(tlo, thi);
                out[1] = Math.max(tlo, thi);
                return;
            }
            out[0] = tlo;
            out[1] = thi;
            return;
        }
        if (df instanceof YClampedGradient g) {
            double denom = (double) g.toY() - g.fromY();
            double v0 = MathHelper.clampedLerp(g.fromValue(), g.toValue(), ((double) yMin - g.fromY()) / denom);
            double v1 = MathHelper.clampedLerp(g.fromValue(), g.toValue(), ((double) yMax - g.fromY()) / denom);
            out[0] = Math.min(v0, v1);
            out[1] = Math.max(v0, v1);
            return;
        }
        if (df instanceof RangeChoice rc) {
            double[] in = new double[2];
            cellBounds(rc.input(), oracle, yMin, yMax, in);
            boolean canInRange  = in[1] >= rc.minInclusive() && in[0] < rc.maxExclusive();
            boolean canOutRange = in[0] < rc.minInclusive() || in[1] >= rc.maxExclusive();
            if (canInRange && !canOutRange) {
                cellBounds(rc.whenInRange(), oracle, yMin, yMax, out);
                return;
            }
            if (!canInRange) {
                cellBounds(rc.whenOutOfRange(), oracle, yMin, yMax, out);
                return;
            }
            double[] a = new double[2];
            double[] b = new double[2];
            cellBounds(rc.whenInRange(), oracle, yMin, yMax, a);
            cellBounds(rc.whenOutOfRange(), oracle, yMin, yMax, b);
            out[0] = Math.min(a[0], b[0]);
            out[1] = Math.max(a[1], b[1]);
            return;
        }
        if (df instanceof BlendDensity bd) {
            cellBounds(bd.input(), oracle, yMin, yMax, out);
            return;
        }
        if (df instanceof Marker mk) {
            cellBounds(mk.wrapped(), oracle, yMin, yMax, out);
            return;
        }
        out[0] = df.minValue();
        out[1] = df.maxValue();
    }

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

        /**
         * SplineCoordinate.mapAll(visitor) 를 통해 스플라인 트리 안의
         * DensityFunction 도 재귀적으로 방문자에 전달한다.
         */
        @Override
        @SuppressWarnings("unchecked")
        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            // coordinate 타입이 SplineCoordinate 인 경우에만 재귀 변환
            CubicSpline<DensityFunction.FunctionContext, ?> s =
                (CubicSpline<DensityFunction.FunctionContext, ?>) spline;
            CubicSpline<DensityFunction.FunctionContext,
                org.domi.furnace.server.world.gen.df.DensityFunctionParser.SplineCoordinate> mapped =
                ((CubicSpline<DensityFunction.FunctionContext,
                    org.domi.furnace.server.world.gen.df.DensityFunctionParser.SplineCoordinate>) s)
                    .mapAll(coord -> coord.mapAll(visitor));
            return visitor.apply(new Spline<>(mapped));
        }
    }
}
