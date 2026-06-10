// 바닐라 density_function JSON 트리를 DensityFunction 인스턴스로 빌드하는 파서
package org.domi.furnace.server.world.gen.df;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.noise.BlendedNoise;
import org.domi.furnace.server.world.gen.noise.NormalNoise;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 바닐라 RandomState 의 노이즈 시딩 방식을 재현한다.
 *
 * 시딩 방식:
 *   positionalFactory = new XoroshiroRandomSource(seed).forkPositional()
 *
 *   NormalNoise (noise/shifted_noise/shift_a/shift_b/shift/weird_scaled_sampler):
 *     NormalNoise.create(positionalFactory.fromHashOf(noiseId), params)
 *     noiseId = 바닐라 ResourceLocation 문자열, e.g. "minecraft:continentalness"
 *
 *   BlendedNoise (old_blended_noise):
 *     new BlendedNoise(positionalFactory.fromHashOf("minecraft:terrain"), ...)
 *     (바닐라 RandomState.java: random.fromHashOf(ResourceLocation.withDefaultNamespace("terrain")))
 */
public final class DensityFunctionParser {

    private final WorldgenData data;
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory positionalFactory;

    /** 이미 파싱한 id → DensityFunction 캐시 (DAG 공유 참조 처리 + 무한루프 방지) */
    private final Map<String, DensityFunction> cache = new HashMap<>();

    public DensityFunctionParser(WorldgenData data, long seed) {
        this.data = data;
        this.positionalFactory = new XoroshiroRandomSource(seed).forkPositional();
    }

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    /**
     * 지정된 id 의 밀도 함수를 빌드한다. 캐시 적중 시 즉시 반환.
     * id 형식: "minecraft:overworld/base_3d_noise"
     */
    public DensityFunction parse(String id) {
        // computeIfAbsent throws ConcurrentModificationException when called recursively
        // (HashMap 은 중첩 computeIfAbsent 를 허용하지 않음). 따라서 수동 체크/풋을 사용한다.
        DensityFunction cached = cache.get(id);
        if (cached != null) return cached;
        DensityFunction result = loadAndParse(id);
        cache.put(id, result);
        return result;
    }

    private DensityFunction loadAndParse(String id) {
        JsonObject root = data.densityFunction(id);
        return parseObject(root);
    }

    // ------------------------------------------------------------------
    // Core dispatch
    // ------------------------------------------------------------------

    /**
     * JsonElement 를 DensityFunction 으로 변환한다.
     *   - 숫자  → constant(value)
     *   - 문자열 → 참조 id 로 재귀 parse()
     *   - 객체  → "type" 필드로 dispatch
     */
    public DensityFunction parseElement(JsonElement e) {
        if (e.isJsonPrimitive()) {
            var prim = e.getAsJsonPrimitive();
            if (prim.isNumber()) {
                return DensityFunctions.constant(prim.getAsDouble());
            }
            if (prim.isString()) {
                return parse(prim.getAsString());
            }
        }
        if (e.isJsonObject()) {
            return parseObject(e.getAsJsonObject());
        }
        throw new IllegalArgumentException("Unexpected JSON element type: " + e);
    }

    // ------------------------------------------------------------------
    // Object dispatch by "type"
    // ------------------------------------------------------------------

    private DensityFunction parseObject(JsonObject obj) {
        // 스플라인 내부의 nested spline 은 "type" 없이 coordinate+points 구조로 나타남.
        // 이 경우 parseSplineNode 로 직접 처리한다.
        if (!obj.has("type") && obj.has("coordinate") && obj.has("points")) {
            return DensityFunctions.spline(parseSplineNode(obj));
        }

        String type = stripNs(obj.get("type").getAsString());

        return switch (type) {
            // -- constant --
            case "constant" -> DensityFunctions.constant(obj.get("argument").getAsDouble());

            // -- blend --
            case "blend_alpha"  -> DensityFunctions.blendAlpha();
            case "blend_offset" -> DensityFunctions.blendOffset();
            case "blend_density" -> DensityFunctions.blendDensity(
                    parseElement(obj.get("argument")));

            // -- beardifier: 건물 배치용, 순수 worldgen 경로에서는 상수 0 으로 처리 --
            case "beardifier" -> DensityFunctions.constant(0.0);

            // -- end_islands: 오버월드에서는 사용 안 함 --
            case "end_islands" ->
                throw new UnsupportedOperationException("end_islands is not used in overworld");

            // -- markers / cache --
            case "interpolated"    -> DensityFunctions.interpolated(parseElement(obj.get("argument")));
            case "flat_cache"      -> DensityFunctions.flatCache(parseElement(obj.get("argument")));
            case "cache_2d"        -> DensityFunctions.cache2d(parseElement(obj.get("argument")));
            case "cache_once"      -> DensityFunctions.cacheOnce(parseElement(obj.get("argument")));
            case "cache_all_in_cell" -> DensityFunctions.cacheAllInCell(parseElement(obj.get("argument")));

            // -- two-arg --
            case "add" -> DensityFunctions.add(
                    parseElement(obj.get("argument1")),
                    parseElement(obj.get("argument2")));
            case "mul" -> DensityFunctions.mul(
                    parseElement(obj.get("argument1")),
                    parseElement(obj.get("argument2")));
            case "min" -> DensityFunctions.min(
                    parseElement(obj.get("argument1")),
                    parseElement(obj.get("argument2")));
            case "max" -> DensityFunctions.max(
                    parseElement(obj.get("argument1")),
                    parseElement(obj.get("argument2")));

            // -- mapped --
            case "abs"              -> DensityFunctions.map(parseElement(obj.get("argument")), DensityFunctions.Mapped.Type.ABS);
            case "square"           -> DensityFunctions.map(parseElement(obj.get("argument")), DensityFunctions.Mapped.Type.SQUARE);
            case "cube"             -> DensityFunctions.map(parseElement(obj.get("argument")), DensityFunctions.Mapped.Type.CUBE);
            case "half_negative"    -> DensityFunctions.map(parseElement(obj.get("argument")), DensityFunctions.Mapped.Type.HALF_NEGATIVE);
            case "quarter_negative" -> DensityFunctions.map(parseElement(obj.get("argument")), DensityFunctions.Mapped.Type.QUARTER_NEGATIVE);
            case "squeeze"          -> DensityFunctions.map(parseElement(obj.get("argument")), DensityFunctions.Mapped.Type.SQUEEZE);

            // -- clamp --
            case "clamp" -> {
                DensityFunction input = parseElement(obj.get("input"));
                double lo = obj.get("min").getAsDouble();
                double hi = obj.get("max").getAsDouble();
                yield new DensityFunctions.Clamp(input, lo, hi);
            }

            // -- range_choice --
            case "range_choice" -> DensityFunctions.rangeChoice(
                    parseElement(obj.get("input")),
                    obj.get("min_inclusive").getAsDouble(),
                    obj.get("max_exclusive").getAsDouble(),
                    parseElement(obj.get("when_in_range")),
                    parseElement(obj.get("when_out_of_range")));

            // -- y_clamped_gradient --
            case "y_clamped_gradient" -> DensityFunctions.yClampedGradient(
                    obj.get("from_y").getAsInt(),
                    obj.get("to_y").getAsInt(),
                    obj.get("from_value").getAsDouble(),
                    obj.get("to_value").getAsDouble());

            // -- spline --
            case "spline" -> DensityFunctions.spline(parseSplineNode(obj.get("spline").getAsJsonObject()));

            // -- noise (minecraft:noise node) --
            case "noise" -> {
                String noiseId = obj.get("noise").getAsString();
                double xzScale = obj.has("xz_scale") ? obj.get("xz_scale").getAsDouble() : 1.0;
                double yScale  = obj.has("y_scale")  ? obj.get("y_scale").getAsDouble()  : 1.0;
                yield DensityFunctions.noise(loadNoise(noiseId), xzScale, yScale);
            }

            // -- shifted_noise --
            case "shifted_noise" -> {
                String noiseId = obj.get("noise").getAsString();
                double xzScale = obj.get("xz_scale").getAsDouble();
                double yScale  = obj.get("y_scale").getAsDouble();
                DensityFunction shiftX = parseElement(obj.get("shift_x"));
                DensityFunction shiftY = parseElement(obj.get("shift_y"));
                DensityFunction shiftZ = parseElement(obj.get("shift_z"));
                yield DensityFunctions.shiftedNoise(shiftX, shiftY, shiftZ, xzScale, yScale, loadNoise(noiseId));
            }

            // -- shift / shift_a / shift_b: "argument" is a noise id string --
            case "shift" -> {
                String noiseId = obj.get("argument").getAsString();
                yield DensityFunctions.shift(loadNoise(noiseId));
            }
            case "shift_a" -> {
                String noiseId = obj.get("argument").getAsString();
                yield DensityFunctions.shiftA(loadNoise(noiseId));
            }
            case "shift_b" -> {
                String noiseId = obj.get("argument").getAsString();
                yield DensityFunctions.shiftB(loadNoise(noiseId));
            }

            // -- weird_scaled_sampler --
            case "weird_scaled_sampler" -> {
                DensityFunction input = parseElement(obj.get("input"));
                String noiseId = obj.get("noise").getAsString();
                String mapperStr = obj.get("rarity_value_mapper").getAsString();
                DensityFunctions.WeirdScaledSampler.RarityValueMapper mapper = switch (mapperStr) {
                    case "type_1" -> DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE1;
                    case "type_2" -> DensityFunctions.WeirdScaledSampler.RarityValueMapper.TYPE2;
                    default -> throw new IllegalArgumentException("Unknown rarity_value_mapper: " + mapperStr);
                };
                yield DensityFunctions.weirdScaledSampler(input, loadNoise(noiseId), mapper);
            }

            // -- old_blended_noise --
            case "old_blended_noise" -> {
                double xzScale = obj.get("xz_scale").getAsDouble();
                double yScale  = obj.get("y_scale").getAsDouble();
                double xzFactor = obj.get("xz_factor").getAsDouble();
                double yFactor  = obj.get("y_factor").getAsDouble();
                double smear    = obj.get("smear_scale_multiplier").getAsDouble();
                // 바닐라 RandomState: random.fromHashOf(ResourceLocation.withDefaultNamespace("terrain"))
                XoroshiroRandomSource rng = positionalFactory.fromHashOf("minecraft:terrain");
                yield DensityFunctions.oldBlendedNoise(new BlendedNoise(rng, xzScale, yScale, xzFactor, yFactor, smear));
            }

            default -> throw new IllegalArgumentException("Unknown density function type: " + type);
        };
    }

    // ------------------------------------------------------------------
    // Noise loading (seeded via positionalFactory.fromHashOf(noiseId))
    // ------------------------------------------------------------------

    /**
     * noiseId (e.g. "minecraft:continentalness") に 対応する NormalNoise を生성한다.
     * 파라미터는 WorldgenData.noiseParams() 에서 로드.
     * 시딩: positionalFactory.fromHashOf(noiseId)
     */
    private NormalNoise loadNoise(String noiseId) {
        JsonElement paramsEl = data.noiseParams(noiseId);
        NormalNoise.NoiseParameters params = parseNoiseParams(paramsEl);
        XoroshiroRandomSource rng = positionalFactory.fromHashOf(noiseId);
        return NormalNoise.create(rng, params);
    }

    private static NormalNoise.NoiseParameters parseNoiseParams(JsonElement el) {
        JsonObject obj = el.getAsJsonObject();
        int firstOctave = obj.get("firstOctave").getAsInt();
        JsonArray ampsArray = obj.get("amplitudes").getAsJsonArray();
        double[] amplitudes = new double[ampsArray.size()];
        for (int i = 0; i < ampsArray.size(); i++) {
            amplitudes[i] = ampsArray.get(i).getAsDouble();
        }
        return new NormalNoise.NoiseParameters(firstOctave, amplitudes);
    }

    // ------------------------------------------------------------------
    // Spline parsing
    // ------------------------------------------------------------------

    /**
     * 바닐라 CubicSpline JSON 형식을 파싱한다.
     *
     * 두 가지 경우:
     *   1. 숫자(float) → CubicSpline.constant(value)
     *   2. 객체 {"coordinate": <id>, "points": [...]} → CubicSpline.Multipoint
     *
     * "coordinate" 는 DensityFunction 의 id 문자열 (reference).
     * ToFloatFunction<FunctionContext> 으로 래핑된 DensityFunction 을 좌표로 사용.
     */
    private CubicSpline<DensityFunction.FunctionContext, SplineCoordinate> parseSplineElement(JsonElement el) {
        if (el.isJsonPrimitive() && el.getAsJsonPrimitive().isNumber()) {
            return CubicSpline.constant((float) el.getAsDouble());
        }
        if (el.isJsonObject()) {
            return parseSplineNode(el.getAsJsonObject());
        }
        throw new IllegalArgumentException("Unexpected spline element: " + el);
    }

    private CubicSpline<DensityFunction.FunctionContext, SplineCoordinate> parseSplineNode(JsonObject obj) {
        // coordinate = DensityFunction id string
        String coordId = obj.get("coordinate").getAsString();
        DensityFunction coordFn = parse(coordId);
        SplineCoordinate coordinate = new SplineCoordinate(coordFn);

        JsonArray pointsArray = obj.get("points").getAsJsonArray();
        float[] locations   = new float[pointsArray.size()];
        float[] derivatives = new float[pointsArray.size()];
        List<CubicSpline<DensityFunction.FunctionContext, SplineCoordinate>> values = new ArrayList<>(pointsArray.size());

        for (int i = 0; i < pointsArray.size(); i++) {
            JsonObject pt = pointsArray.get(i).getAsJsonObject();
            locations[i]   = pt.get("location").getAsFloat();
            derivatives[i] = pt.get("derivative").getAsFloat();
            values.add(parseSplineElement(pt.get("value")));
        }

        return CubicSpline.Multipoint.create(coordinate, locations, values, derivatives);
    }

    // ------------------------------------------------------------------
    // SplineCoordinate: ToFloatFunction<FunctionContext> wrapping a DensityFunction
    // ------------------------------------------------------------------

    /**
     * 바닐라 DensityFunctions.Spline.Coordinate 에 대응.
     * DensityFunction.compute() 결과를 float 로 캐스트.
     * minValue/maxValue 는 DensityFunction 의 값 범위에서 산출.
     */
    public static final class SplineCoordinate
            implements ToFloatFunction<DensityFunction.FunctionContext> {

        private final DensityFunction function;

        public SplineCoordinate(DensityFunction function) {
            this.function = function;
        }

        @Override
        public float apply(DensityFunction.FunctionContext ctx) {
            return (float) function.compute(ctx);
        }

        @Override
        public float minValue() {
            return (float) function.minValue();
        }

        @Override
        public float maxValue() {
            return (float) function.maxValue();
        }

        /** DensityFunction.Visitor 로 내부 함수를 재귀 변환한다. */
        public SplineCoordinate mapAll(DensityFunction.Visitor visitor) {
            return new SplineCoordinate(function.mapAll(visitor));
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** "minecraft:flat_cache" → "flat_cache" */
    private static String stripNs(String type) {
        int colon = type.indexOf(':');
        return colon >= 0 ? type.substring(colon + 1) : type;
    }
}
