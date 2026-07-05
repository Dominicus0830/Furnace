package org.domi.furnace.server.world.gen.feature;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.OptionalInt;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.feature.decorator.CocoaDecorator;
import org.domi.furnace.server.world.gen.feature.decorator.BeehiveDecorator;
import org.domi.furnace.server.world.gen.feature.decorator.LeaveVineDecorator;
import org.domi.furnace.server.world.gen.feature.decorator.TreeDecorator;
import org.domi.furnace.server.world.gen.feature.decorator.TrunkVineDecorator;
import org.domi.furnace.server.world.gen.feature.foliage.AcaciaFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.BlobFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.DarkOakFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.FoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.PineFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.SpruceFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.size.FeatureSize;
import org.domi.furnace.server.world.gen.feature.size.ThreeLayersFeatureSize;
import org.domi.furnace.server.world.gen.feature.size.TwoLayersFeatureSize;
import org.domi.furnace.server.world.gen.feature.trunk.DarkOakTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.ForkingTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.StraightTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;

public final class BiomeFeatureData {

    public static final int STEP_COUNT = 11;

    private final Map<String, List<List<PlacedFeature>>> biomeFeatures; // 삽입(=load) 순서 보존
    private final Map<PlacedFeature, String> idByFeature; // identity → placed feature id (parity 검증용)
    private final IdentityHashMap<PlacedFeature, Set<Integer>> featureBiomeIds; // feature → biome ID set (biome filter용)

    public BiomeFeatureData(Map<String, List<List<PlacedFeature>>> biomeFeatures) {
        this(biomeFeatures, new IdentityHashMap<>(), new IdentityHashMap<>());
    }

    public BiomeFeatureData(Map<String, List<List<PlacedFeature>>> biomeFeatures,
                            Map<PlacedFeature, String> idByFeature) {
        this(biomeFeatures, idByFeature, new IdentityHashMap<>());
    }

    public BiomeFeatureData(Map<String, List<List<PlacedFeature>>> biomeFeatures,
                            Map<PlacedFeature, String> idByFeature,
                            IdentityHashMap<PlacedFeature, Set<Integer>> featureBiomeIds) {
        this.biomeFeatures = Collections.unmodifiableMap(new LinkedHashMap<>(biomeFeatures));
        this.idByFeature = Collections.unmodifiableMap(new IdentityHashMap<>(idByFeature));
        this.featureBiomeIds = new IdentityHashMap<>(featureBiomeIds);
    }

    /** PlacedFeature identity → 원본 id. 미구현(placeholder) 포함. */
    public String idOf(PlacedFeature feature) {
        return idByFeature.get(feature);
    }

    /** PlacedFeature → 이 feature를 보유한 biome ID set (vanilla biome placement filter용). */
    public Set<Integer> validBiomeIds(PlacedFeature feature) {
        return featureBiomeIds.getOrDefault(feature, Set.of());
    }

    public static BiomeFeatureData load(WorldgenData data, Iterable<String> biomeIds, BlockRegistry registry) {
        return load(data, biomeIds, null, registry);
    }

    /**
     * @param registryOrder biome sampler와 동일한 registry 인덱스 순서. null이면 biomeIds 순서를 그대로 사용.
     *                      possibleBiomes(feature sorter 순서)와 다를 수 있어 validBiomeIds 기준으로 별도 전달.
     */
    public static BiomeFeatureData load(WorldgenData data, Iterable<String> biomeIds,
                                        List<String> registryOrder, BlockRegistry registry) {
        // biome id(= biomeIds 열거 순서, registry biome 팔레트와 동일)별 base 온도 — FreezeTopLayer 동결 게이트용.
        // 온도 데이터 없는 픽스처(worldgen/test 등)면 빈 배열 → 기본 0.5(동결 안 함)로 폴백.
        float[] biomeTemps;
        try {
            ArrayList<Float> temps = new ArrayList<>();
            for (String biomeId : biomeIds) temps.add(data.biomeTemperature(biomeId));
            biomeTemps = new float[temps.size()];
            for (int i = 0; i < biomeTemps.length; i++) biomeTemps[i] = temps.get(i);
        } catch (IllegalStateException e) {
            biomeTemps = new float[0];
        }

        Loader loader = new Loader(data, registry, biomeTemps);
        Map<String, List<List<PlacedFeature>>> loaded = new LinkedHashMap<>();
        for (String biomeId : biomeIds) {
            JsonObject biome = data.biome(biomeId);
            JsonArray features = biome.getAsJsonArray("features");
            ArrayList<List<PlacedFeature>> steps = new ArrayList<>(STEP_COUNT);
            for (int step = 0; step < STEP_COUNT; step++) {
                ArrayList<PlacedFeature> stepFeatures = new ArrayList<>();
                if (features != null && step < features.size()) {
                    // 미구현 feature도 placeholder로 유지 — index parity 보존(vanilla와 동일 순서).
                    for (JsonElement featureId : features.get(step).getAsJsonArray()) {
                        stepFeatures.add(loader.placedFeature(featureId.getAsString()));
                    }
                }
                steps.add(List.copyOf(stepFeatures));
            }
            loaded.put(biomeId, List.copyOf(steps));
        }
        IdentityHashMap<PlacedFeature, String> idByFeature = new IdentityHashMap<>();
        loader.placedCache.forEach((id, pf) -> idByFeature.put(pf, id));

        // feature → biome ID set (biome placement filter용 역방향 맵)
        // biome ID = registryOrder 기반 인덱스 (sampleBiomes와 동일 기준). null이면 biomeIds 순서.
        List<String> biomeList = new ArrayList<>();
        for (String b : biomeIds) biomeList.add(b);
        List<String> idxSource = (registryOrder != null) ? registryOrder : biomeList;
        IdentityHashMap<PlacedFeature, Set<Integer>> featureBiomeIds = new IdentityHashMap<>();
        for (String biomeName : biomeList) {
            int registryIdx = idxSource.indexOf(biomeName);
            if (registryIdx < 0) continue;
            List<List<PlacedFeature>> steps = loaded.get(biomeName);
            if (steps == null) continue;
            for (List<PlacedFeature> step : steps) {
                for (PlacedFeature pf : step) {
                    if (pf == null) continue;
                    featureBiomeIds.computeIfAbsent(pf, k -> new HashSet<>()).add(registryIdx);
                }
            }
        }
        IdentityHashMap<PlacedFeature, Set<Integer>> frozenBiomeIds = new IdentityHashMap<>();
        featureBiomeIds.forEach((pf, ids) -> frozenBiomeIds.put(pf, Set.copyOf(ids)));

        return new BiomeFeatureData(loaded, idByFeature, frozenBiomeIds);
    }

    public Set<String> biomes() {
        return biomeFeatures.keySet();
    }

    /** load 순서(=registry biome 순서)를 보존한 biome 목록. FeatureSorter 결정성에 필요. */
    public List<String> orderedBiomes() {
        return new ArrayList<>(biomeFeatures.keySet());
    }

    public List<PlacedFeature> featuresForStep(String biomeId, int step) {
        List<List<PlacedFeature>> steps = biomeFeatures.get(biomeId);
        if (steps == null || step < 0 || step >= steps.size()) return List.of();
        return steps.get(step);
    }

    static List<List<PlacedFeature>> emptySteps() {
        ArrayList<List<PlacedFeature>> steps = new ArrayList<>(STEP_COUNT);
        for (int i = 0; i < STEP_COUNT; i++) steps.add(List.of());
        return steps;
    }

    private static final class Loader {
        private final WorldgenData data;
        private final BlockRegistry registry;
        private final float[] biomeTemps;
        private final Map<String, PlacedFeature> placedCache = new HashMap<>();
        private final Map<String, ConfiguredFeature> configuredCache = new HashMap<>();

        Loader(WorldgenData data, BlockRegistry registry, float[] biomeTemps) {
            this.data = data;
            this.registry = registry;
            this.biomeTemps = biomeTemps;
        }

        PlacedFeature placedFeature(String id) {
            PlacedFeature cached = placedCache.get(id);
            if (cached != null || placedCache.containsKey(id)) return cached;
            JsonObject json = data.placedFeature(id);
            ConfiguredFeature feature = configuredFeature(featureId(json.get("feature")));
            if (feature == null) {
                placedCache.put(id, null);
                return null;
            }
            ArrayList<PlacementModifier> placement = new ArrayList<>();
            JsonArray arr = json.getAsJsonArray("placement");
            if (arr != null) {
                for (JsonElement el : arr) parsePlacement(el.getAsJsonObject(), placement);
            }
            PlacedFeature created = new PlacedFeature(feature, List.copyOf(placement));
            placedCache.put(id, created);
            return created;
        }

        private static String featureId(JsonElement el) {
            if (el.isJsonPrimitive()) return el.getAsString();
            JsonObject obj = el.getAsJsonObject();
            return obj.has("name") ? obj.get("name").getAsString() : obj.get("id").getAsString();
        }

        ConfiguredFeature configuredFeature(String id) {
            ConfiguredFeature cached = configuredCache.get(id);
            if (cached != null || configuredCache.containsKey(id)) return cached;
            ConfiguredFeature created = parseConfigured(data.configuredFeature(id));
            configuredCache.put(id, created);
            return created;
        }

        private ConfiguredFeature parseConfigured(JsonObject json) {
            String type = json.get("type").getAsString();
            JsonObject config = json.getAsJsonObject("config");
            if (type.equals("minecraft:ore") || type.equals("minecraft:scattered_ore")) {
                return parseOre(config);
            }
            if (type.equals("minecraft:spring_feature")) {
                return parseSpring(config);
            }
            if (type.equals("minecraft:disk")) {
                return parseDisk(config);
            }
            if (type.equals("minecraft:random_patch") || type.equals("minecraft:flower")) {
                // FlowerFeature는 RandomPatchFeature와 place 동일.
                return parseRandomPatch(config);
            }
            if (type.equals("minecraft:simple_block")) {
                return parseSimpleBlock(config);
            }
            if (type.equals("minecraft:seagrass")) {
                return parseSeagrass(config);
            }
            if (type.equals("minecraft:kelp")) {
                return parseKelp();
            }
            if (type.equals("minecraft:freeze_top_layer")) {
                return new FreezeTopLayerFeature(
                    registry.stateIdOrAir("minecraft:water"),
                    registry.stateIdOrAir("minecraft:ice"),
                    registry.stateIdOrAir("minecraft:snow"),
                    biomeTemps);
            }
            if (type.equals("minecraft:geode")) {
                return parseGeode(config);
            }
            if (type.equals("minecraft:tree")) {
                return parseTree(config);
            }
            if (type.equals("minecraft:random_selector")) {
                return parseRandomSelector(config);
            }
            if (type.equals("minecraft:random_boolean_selector")) {
                return parseRandomBooleanSelector(config);
            }
            if (type.equals("minecraft:simple_random_selector")) {
                return parseSimpleRandomSelector(config);
            }
            if (type.equals("minecraft:underwater_magma")) {
                return parseUnderwaterMagma(config);
            }
            if (type.equals("minecraft:multiface_growth")) {
                return parseMultifaceGrowth(config);
            }
            // 미구현 feature 타입 → placeholder. index parity 유지(Phase 2에서 실제 구현으로 교체).
            return NoopFeature.INSTANCE;
        }

        private ConfiguredFeature parseRandomPatch(JsonObject config) {
            // config.feature = 인라인 placed feature {feature: configured, placement: [...]}
            JsonObject inlinePlaced = config.getAsJsonObject("feature");
            ConfiguredFeature inner = parseConfigured(inlinePlaced.getAsJsonObject("feature"));
            // 비상수 provider 등 미지원 inner → placeholder 유지(거짓 parity 방지)
            if (!(inner instanceof SimpleBlockFeature)) {
                return NoopFeature.INSTANCE;
            }
            ArrayList<PlacementModifier> placement = new ArrayList<>();
            JsonArray arr = inlinePlaced.getAsJsonArray("placement");
            if (arr != null) {
                for (JsonElement el : arr) parsePlacement(el.getAsJsonObject(), placement);
            }
            PlacedFeature innerPlaced = new PlacedFeature(inner, List.copyOf(placement));
            int tries = config.has("tries") ? config.get("tries").getAsInt() : 128;
            int xz = config.has("xz_spread") ? config.get("xz_spread").getAsInt() : 7;
            int y = config.has("y_spread") ? config.get("y_spread").getAsInt() : 3;
            return new RandomPatchFeature(tries, xz, y, innerPlaced);
        }

        private ConfiguredFeature parseRandomSelector(JsonObject config) {
            ArrayList<RandomSelectorFeature.Entry> entries = new ArrayList<>();
            JsonArray arr = config.getAsJsonArray("features");
            if (arr != null) {
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    entries.add(new RandomSelectorFeature.Entry(
                        obj.get("chance").getAsFloat(),
                        parseSelectorFeature(obj.get("feature"))));
                }
            }
            return new RandomSelectorFeature(List.copyOf(entries), parseSelectorFeature(config.get("default")));
        }

        private ConfiguredFeature parseRandomBooleanSelector(JsonObject config) {
            return new RandomBooleanSelectorFeature(
                parseSelectorFeature(config.get("feature_true")),
                parseSelectorFeature(config.get("feature_false")));
        }

        private ConfiguredFeature parseSimpleRandomSelector(JsonObject config) {
            ArrayList<PlacedFeature> entries = new ArrayList<>();
            JsonArray arr = config.getAsJsonArray("features");
            if (arr != null) {
                for (JsonElement el : arr) entries.add(parseSelectorFeature(el));
            }
            return new SimpleRandomSelectorFeature(List.copyOf(entries));
        }

        private PlacedFeature parseSelectorFeature(JsonElement el) {
            if (el.isJsonPrimitive()) {
                return placedFeature(el.getAsString());
            }
            return parseInlinePlacedFeature(el);
        }

        private PlacedFeature parseInlinePlacedFeature(JsonElement el) {
            if (el.isJsonPrimitive()) return placedFeature(el.getAsString());
            JsonObject obj = el.getAsJsonObject();
            ConfiguredFeature feature = obj.has("feature")
                ? (obj.get("feature").isJsonPrimitive()
                    ? configuredFeature(obj.get("feature").getAsString())
                    : parseConfigured(obj.getAsJsonObject("feature")))
                : configuredFeature(featureId(obj));
            ArrayList<PlacementModifier> placement = new ArrayList<>();
            JsonArray arr = obj.getAsJsonArray("placement");
            if (arr != null) {
                for (JsonElement placementEl : arr) parsePlacement(placementEl.getAsJsonObject(), placement);
            }
            return new PlacedFeature(feature, List.copyOf(placement));
        }

        private ConfiguredFeature parseSimpleBlock(JsonObject config) {
            StateProvider provider = parseToPlaceProvider(config.getAsJsonObject("to_place"));
            if (provider == null) {
                // 미지원 provider(weighted/dual_noise/noise) → placeholder(거짓 parity 방지).
                return NoopFeature.INSTANCE;
            }
            return new SimpleBlockFeature(provider, plantGround());
        }

        /** simple_state_provider / noise_threshold_provider 지원. 그외 → null(미지원). */
        private StateProvider parseToPlaceProvider(JsonObject toPlace) {
            String type = toPlace.has("type")
                ? toPlace.get("type").getAsString() : "minecraft:simple_state_provider";
            switch (type) {
                case "minecraft:simple_state_provider" -> {
                    return new SimpleStateProvider(parseStateProvider(toPlace));
                }
                case "minecraft:noise_threshold_provider" -> {
                    JsonObject noise = toPlace.getAsJsonObject("noise");
                    JsonArray ampArr = noise.getAsJsonArray("amplitudes");
                    double[] amplitudes = new double[ampArr.size()];
                    for (int i = 0; i < amplitudes.length; i++) amplitudes[i] = ampArr.get(i).getAsDouble();
                    int[] lowStates = blockStateArray(toPlace.getAsJsonArray("low_states"));
                    int[] highStates = blockStateArray(toPlace.getAsJsonArray("high_states"));
                    return new NoiseThresholdProvider(
                        toPlace.get("seed").getAsLong(),
                        noise.get("firstOctave").getAsInt(),
                        amplitudes,
                        toPlace.get("scale").getAsFloat(),
                        toPlace.get("threshold").getAsFloat(),
                        toPlace.get("high_chance").getAsFloat(),
                        parseState(toPlace.getAsJsonObject("default_state")),
                        lowStates, highStates);
                }
                case "minecraft:weighted_state_provider" -> {
                    JsonArray entries = toPlace.getAsJsonArray("entries");
                    int[] wStates = new int[entries.size()];
                    int[] weights = new int[entries.size()];
                    for (int i = 0; i < entries.size(); i++) {
                        JsonObject entry = entries.get(i).getAsJsonObject();
                        wStates[i] = parseState(entry.getAsJsonObject("data"));
                        weights[i] = entry.get("weight").getAsInt();
                    }
                    return new WeightedStateProvider(wStates, weights);
                }
                case "minecraft:noise_provider" -> {
                    JsonArray states = toPlace.getAsJsonArray("states");
                    JsonObject noise = toPlace.getAsJsonObject("noise");
                    return new NoiseProvider(
                        toPlace.get("seed").getAsLong(),
                        noise.get("firstOctave").getAsInt(),
                        amplitudeArray(noise),
                        toPlace.get("scale").getAsFloat(),
                        blockStateArray(states));
                }
                case "minecraft:dual_noise_provider" -> {
                    JsonArray states = toPlace.getAsJsonArray("states");
                    JsonObject noise = toPlace.getAsJsonObject("noise");
                    JsonObject slow = toPlace.getAsJsonObject("slow_noise");
                    JsonArray variety = toPlace.getAsJsonArray("variety");
                    long seed = toPlace.get("seed").getAsLong();
                    return new DualNoiseProvider(
                        variety.get(0).getAsInt(), variety.get(1).getAsInt(),
                        seed, slow.get("firstOctave").getAsInt(), amplitudeArray(slow),
                        toPlace.get("slow_scale").getAsFloat(),
                        seed, noise.get("firstOctave").getAsInt(), amplitudeArray(noise),
                        toPlace.get("scale").getAsFloat(),
                        blockStateArray(states));
                }
                default -> {
                    return null;
                }
            }
        }

        private static double[] amplitudeArray(JsonObject noise) {
            JsonArray ampArr = noise.getAsJsonArray("amplitudes");
            double[] amplitudes = new double[ampArr.size()];
            for (int i = 0; i < amplitudes.length; i++) amplitudes[i] = ampArr.get(i).getAsDouble();
            return amplitudes;
        }

        private int[] blockStateArray(JsonArray arr) {
            int[] ids = new int[arr.size()];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = parseState(arr.get(i).getAsJsonObject());
            }
            return ids;
        }

        // BushBlock.mayPlaceOn 근사 = dirt 태그 + farmland. grass/fern 등 지면식물 생존 판정용.
        // 블록별 정확 survival은 추후(여기선 흙 계열로 근사).
        private Set<Integer> plantGround() {
            if (plantGroundCache == null) {
                plantGroundCache = new HashSet<>(states(
                    "minecraft:grass_block", "minecraft:dirt", "minecraft:coarse_dirt",
                    "minecraft:podzol", "minecraft:mycelium", "minecraft:rooted_dirt",
                    "minecraft:moss_block", "minecraft:mud", "minecraft:muddy_mangrove_roots",
                    "minecraft:farmland"));
            }
            return plantGroundCache;
        }
        private Set<Integer> plantGroundCache;

        private OreFeature parseOre(JsonObject config) {
            ArrayList<OreFeature.Target> targets = new ArrayList<>();
            for (JsonElement el : config.getAsJsonArray("targets")) {
                JsonObject targetObj = el.getAsJsonObject();
                List<Integer> replaceIds = parseTargetPredicate(targetObj.getAsJsonObject("target"));
                int stateId = parseState(targetObj.getAsJsonObject("state"));
                targets.add(new OreFeature.Target(replaceIds, stateId));
            }
            int size = config.get("size").getAsInt();
            float discard = config.has("discard_chance_on_air_exposure")
                ? config.get("discard_chance_on_air_exposure").getAsFloat()
                : 0.0f;
            return new OreFeature(targets, size, discard);
        }

        private SpringFeature parseSpring(JsonObject config) {
            return new SpringFeature(
                parseState(config.getAsJsonObject("state")),
                config.has("requires_block_below") && config.get("requires_block_below").getAsBoolean(),
                config.has("rock_count") ? config.get("rock_count").getAsInt() : 4,
                config.has("hole_count") ? config.get("hole_count").getAsInt() : 1,
                parseBlockSet(config.get("valid_blocks")));
        }

        private SeagrassFeature parseSeagrass(JsonObject config) {
            return new SeagrassFeature(
                config.get("probability").getAsFloat(),
                registry.stateIdOrAir("minecraft:seagrass"),
                registry.stateIdOrAir("minecraft:tall_seagrass"),
                registry.stateIdOrAir("minecraft:tall_seagrass", Map.of("half", "upper")),
                registry.stateIdOrAir("minecraft:magma_block"));
        }

        private KelpFeature parseKelp() {
            int[] kelpTopStates = new int[4];
            for (int age = 20; age <= 23; age++) {
                kelpTopStates[age - 20] = registry.stateIdOrAir("minecraft:kelp", Map.of("age", Integer.toString(age)));
            }
            return new KelpFeature(
                registry.stateIdOrAir("minecraft:kelp"),
                registry.stateIdOrAir("minecraft:kelp_plant"),
                kelpTopStates,
                registry.stateIdOrAir("minecraft:magma_block"));
        }

        private UnderwaterMagmaFeature parseUnderwaterMagma(JsonObject config) {
            int floorSearchRange = config.has("floor_search_range") ? config.get("floor_search_range").getAsInt() : 5;
            int radius = config.has("placement_radius_around_floor") ? config.get("placement_radius_around_floor").getAsInt() : 1;
            float probability = config.has("placement_probability_per_valid_position") ? config.get("placement_probability_per_valid_position").getAsFloat() : 0.5f;
            return new UnderwaterMagmaFeature(floorSearchRange, radius, probability);
        }

        private ConfiguredFeature parseMultifaceGrowth(JsonObject config) {
            String blockName = config.has("block") ? blockName(config.get("block")) : "minecraft:glow_lichen";
            if (!"minecraft:glow_lichen".equals(blockName)) {
                return NoopFeature.INSTANCE;
            }

            int searchRange = config.has("search_range") ? config.get("search_range").getAsInt() : 20;
            boolean canPlaceOnCeiling = !config.has("can_place_on_ceiling") || config.get("can_place_on_ceiling").getAsBoolean();
            boolean canPlaceOnFloor = config.has("can_place_on_floor") && config.get("can_place_on_floor").getAsBoolean();
            boolean canPlaceOnWall = !config.has("can_place_on_wall") || config.get("can_place_on_wall").getAsBoolean();
            float chanceOfSpreading = config.has("chance_of_spreading") ? config.get("chance_of_spreading").getAsFloat() : 0.5f;
            Set<Integer> canBePlacedOn = new HashSet<>();
            JsonArray blocks = config.getAsJsonArray("can_be_placed_on");
            if (blocks != null) {
                for (JsonElement el : blocks) {
                    canBePlacedOn.addAll(parseTagReference(el));
                }
            }
            return new GlowLichenFeature(
                searchRange,
                canPlaceOnCeiling,
                canPlaceOnFloor,
                canPlaceOnWall,
                chanceOfSpreading,
                canBePlacedOn,
                registry.stateIdOrAir("minecraft:glow_lichen"));
        }

        private ConfiguredFeature parseDisk(JsonObject config) {
            // state_provider = rule_based: rules[].if_true(matching_blocks, offset [0,-1,0]) + fallback(simple)
            JsonObject sp = config.getAsJsonObject("state_provider");
            // rule_based가 아닌 옛 포맷(simple_state_provider 직접)도 허용 — rules 없음
            JsonObject fallback = sp.has("fallback") ? sp.getAsJsonObject("fallback") : sp;
            if (!"minecraft:simple_state_provider".equals(fallback.get("type").getAsString())) {
                return NoopFeature.INSTANCE;
            }
            int fallbackId = registry.stateIdOrAir(fallback.getAsJsonObject("state").get("Name").getAsString());
            java.util.List<DiskFeature.Rule> rules = new java.util.ArrayList<>();
            for (JsonElement rel : sp.has("rules") ? sp.getAsJsonArray("rules") : new JsonArray()) {
                JsonObject rule = rel.getAsJsonObject();
                JsonObject cond = rule.getAsJsonObject("if_true");
                JsonObject then = rule.getAsJsonObject("then");
                if (!"minecraft:matching_blocks".equals(cond.get("type").getAsString())
                    || !"minecraft:simple_state_provider".equals(then.get("type").getAsString())) {
                    return NoopFeature.INSTANCE;
                }
                JsonElement offset = cond.get("offset");
                if (offset == null || !offset.toString().equals("[0,-1,0]")) {
                    return NoopFeature.INSTANCE;
                }
                rules.add(new DiskFeature.Rule(
                    parseBlockSet(cond.get("blocks")),
                    registry.stateIdOrAir(then.getAsJsonObject("state").get("Name").getAsString())));
            }
            return new DiskFeature(
                fallbackId,
                rules,
                treeIntProvider(config.get("radius"), 2),
                intProviderValue(config.get("half_height"), 1),
                parseTargetBlocks(config.getAsJsonObject("target")));
        }

        private GeodeFeature parseGeode(JsonObject config) {
            JsonObject blocks = config.getAsJsonObject("blocks");
            JsonObject layers = config.getAsJsonObject("layers");
            JsonObject crack = config.getAsJsonObject("crack");
            JsonArray placements = blocks.getAsJsonArray("inner_placements");
            String[] placementNames = new String[placements.size()];
            for (int i = 0; i < placements.size(); i++) {
                placementNames[i] = blockName(placements.get(i));
            }
            return new GeodeFeature(new GeodeFeature.GeodeConfig(
                parseStateProvider(blocks.getAsJsonObject("filling_provider")),
                parseStateProvider(blocks.getAsJsonObject("inner_layer_provider")),
                parseStateProvider(blocks.getAsJsonObject("alternate_inner_layer_provider")),
                parseStateProvider(blocks.getAsJsonObject("middle_layer_provider")),
                parseStateProvider(blocks.getAsJsonObject("outer_layer_provider")),
                placementNames,
                parseTagReference(blocks.get("cannot_replace")),
                parseTagReference(blocks.get("invalid_blocks")),
                layers.get("filling").getAsDouble(),
                layers.get("inner_layer").getAsDouble(),
                layers.get("middle_layer").getAsDouble(),
                layers.get("outer_layer").getAsDouble(),
                crack.get("generate_crack_chance").getAsDouble(),
                crack.get("base_crack_size").getAsDouble(),
                crack.get("crack_point_offset").getAsInt(),
                config.get("use_potential_placements_chance").getAsDouble(),
                config.get("use_alternate_layer0_chance").getAsDouble(),
                config.get("placements_require_layer0_alternate").getAsBoolean(),
                intRange(config.getAsJsonObject("outer_wall_distance")),
                intRange(config.getAsJsonObject("distribution_points")),
                intRange(config.getAsJsonObject("point_offset")),
                config.get("min_gen_offset").getAsInt(),
                config.get("max_gen_offset").getAsInt(),
                config.get("noise_multiplier").getAsDouble(),
                config.get("invalid_blocks_threshold").getAsInt(),
                registry.stateIdOrAir("minecraft:air"),
                registry::stateIdOrAir));
        }

        private ConfiguredFeature parseTree(JsonObject config) {
            int trunkState = parseStateProvider(config.getAsJsonObject("trunk_provider"));
            JsonObject foliageProvider = config.getAsJsonObject("foliage_provider");
            String leafName = foliageProvider.getAsJsonObject("state").get("Name").getAsString();
            int dirtState = config.has("dirt_provider")
                ? parseStateProvider(config.getAsJsonObject("dirt_provider"))
                : registry.stateIdOrAir("minecraft:dirt");
            return new TreeFeature(new TreeConfig(
                trunkState,
                dirtState,
                leafName,
                buildLeafStates(leafName),
                parseTrunkPlacer(config.getAsJsonObject("trunk_placer")),
                parseFoliagePlacer(config.getAsJsonObject("foliage_placer")),
                parseFeatureSize(config.getAsJsonObject("minimum_size")),
                parseTreeDecorators(config.getAsJsonArray("decorators")),
                config.has("ignore_vines") && config.get("ignore_vines").getAsBoolean(),
                config.has("force_dirt") && config.get("force_dirt").getAsBoolean(),
                buildTreeDirtStates(),
                buildTreeDirtReplaceExemptStates(),
                buildReplaceableByTreesStates(),
                buildLogStates(),
                buildCocoaStates(),
                buildVineStates(),
                buildBeeNestState()));
        }

        private int[][] buildLeafStates(String leafName) {
            int[][] ids = new int[2][7];
            for (int water = 0; water <= 1; water++) {
                for (int distance = 1; distance <= 7; distance++) {
                    ids[water][distance - 1] = registry.stateIdOrAir(leafName, Map.of(
                        "distance", Integer.toString(distance),
                        "persistent", "false",
                        "waterlogged", Boolean.toString(water == 1)));
                }
            }
            return ids;
        }

        private int[] buildCocoaStates() {
            int[] ids = new int[12];
            int idx = 0;
            for (int age = 0; age < 3; age++) {
                for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                    ids[idx++] = registry.stateIdOrAir("minecraft:cocoa", Map.of(
                        "age", Integer.toString(age),
                        "facing", dir.propertyName()));
                }
            }
            return ids;
        }

        private int buildBeeNestState() {
            return registry.stateIdOrAir("minecraft:bee_nest", Map.of(
                "facing", "south",
                "honey_level", "0"));
        }

        private int[] buildVineStates() {
            int[] ids = new int[4];
            for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                ids[dir.horizontalIndex()] = registry.stateIdOrAir("minecraft:vine", Map.of(dir.propertyName(), "true"));
            }
            return ids;
        }

        private Set<Integer> buildTreeDirtStates() {
            return unionBlockStates(
                "minecraft:dirt",
                "minecraft:grass_block",
                "minecraft:podzol",
                "minecraft:coarse_dirt",
                "minecraft:mycelium",
                "minecraft:rooted_dirt",
                "minecraft:moss_block",
                "minecraft:mud",
                "minecraft:muddy_mangrove_roots");
        }

        private Set<Integer> buildTreeDirtReplaceExemptStates() {
            return unionBlockStates("minecraft:grass_block", "minecraft:mycelium");
        }

        private Set<Integer> buildReplaceableByTreesStates() {
            return unionBlockStates(
                "minecraft:short_grass",
                "minecraft:fern",
                "minecraft:dead_bush",
                "minecraft:vine",
                "minecraft:glow_lichen",
                "minecraft:sunflower",
                "minecraft:lilac",
                "minecraft:rose_bush",
                "minecraft:peony",
                "minecraft:tall_grass",
                "minecraft:large_fern",
                "minecraft:hanging_roots",
                "minecraft:pitcher_plant",
                "minecraft:water",
                "minecraft:seagrass",
                "minecraft:tall_seagrass",
                "minecraft:warped_roots",
                "minecraft:nether_sprouts",
                "minecraft:crimson_roots",
                "minecraft:oak_leaves",
                "minecraft:spruce_leaves",
                "minecraft:birch_leaves",
                "minecraft:jungle_leaves",
                "minecraft:acacia_leaves",
                "minecraft:dark_oak_leaves",
                "minecraft:mangrove_leaves",
                "minecraft:cherry_leaves",
                "minecraft:azalea_leaves",
                "minecraft:flowering_azalea_leaves",
                "minecraft:pale_oak_leaves");
        }

        private Set<Integer> buildLogStates() {
            return unionBlockStates(
                "minecraft:oak_log",
                "minecraft:spruce_log",
                "minecraft:birch_log",
                "minecraft:jungle_log",
                "minecraft:acacia_log",
                "minecraft:dark_oak_log",
                "minecraft:mangrove_log",
                "minecraft:cherry_log",
                "minecraft:pale_oak_log",
                "minecraft:oak_wood",
                "minecraft:spruce_wood",
                "minecraft:birch_wood",
                "minecraft:jungle_wood",
                "minecraft:acacia_wood",
                "minecraft:dark_oak_wood",
                "minecraft:mangrove_wood",
                "minecraft:cherry_wood",
                "minecraft:pale_oak_wood",
                "minecraft:stripped_oak_log",
                "minecraft:stripped_spruce_log",
                "minecraft:stripped_birch_log",
                "minecraft:stripped_jungle_log",
                "minecraft:stripped_acacia_log",
                "minecraft:stripped_dark_oak_log",
                "minecraft:stripped_mangrove_log",
                "minecraft:stripped_cherry_log",
                "minecraft:stripped_pale_oak_log",
                "minecraft:stripped_oak_wood",
                "minecraft:stripped_spruce_wood",
                "minecraft:stripped_birch_wood",
                "minecraft:stripped_jungle_wood",
                "minecraft:stripped_acacia_wood",
                "minecraft:stripped_dark_oak_wood",
                "minecraft:stripped_mangrove_wood",
                "minecraft:stripped_cherry_wood",
                "minecraft:stripped_pale_oak_wood",
                "minecraft:crimson_stem",
                "minecraft:warped_stem",
                "minecraft:stripped_crimson_stem",
                "minecraft:stripped_warped_stem",
                "minecraft:crimson_hyphae",
                "minecraft:warped_hyphae",
                "minecraft:stripped_crimson_hyphae",
                "minecraft:stripped_warped_hyphae");
        }

        private Set<Integer> unionBlockStates(String... names) {
            HashSet<Integer> ids = new HashSet<>();
            for (String name : names) ids.addAll(registry.stateIdsForBlock(name));
            ids.remove(0);
            return Set.copyOf(ids);
        }

        private TrunkPlacer parseTrunkPlacer(JsonObject obj) {
            int baseHeight = obj.get("base_height").getAsInt();
            int randA = obj.get("height_rand_a").getAsInt();
            int randB = obj.get("height_rand_b").getAsInt();
            return switch (obj.get("type").getAsString()) {
                case "minecraft:straight_trunk_placer" -> new StraightTrunkPlacer(baseHeight, randA, randB);
                case "minecraft:forking_trunk_placer" -> new ForkingTrunkPlacer(baseHeight, randA, randB);
                case "minecraft:dark_oak_trunk_placer" -> new DarkOakTrunkPlacer(baseHeight, randA, randB);
                default -> new StraightTrunkPlacer(baseHeight, randA, randB);
            };
        }

        private FoliagePlacer parseFoliagePlacer(JsonObject obj) {
            TreeIntProvider radius = treeIntProvider(obj.get("radius"), 0);
            TreeIntProvider offset = treeIntProvider(obj.get("offset"), 0);
            return switch (obj.get("type").getAsString()) {
                case "minecraft:blob_foliage_placer" -> new BlobFoliagePlacer(radius, offset, obj.get("height").getAsInt());
                case "minecraft:spruce_foliage_placer" -> new SpruceFoliagePlacer(radius, offset, treeIntProvider(obj.get("trunk_height"), 1));
                case "minecraft:pine_foliage_placer" -> new PineFoliagePlacer(radius, offset, treeIntProvider(obj.get("height"), 3));
                case "minecraft:acacia_foliage_placer" -> new AcaciaFoliagePlacer(radius, offset);
                case "minecraft:dark_oak_foliage_placer" -> new DarkOakFoliagePlacer(radius, offset);
                default -> new BlobFoliagePlacer(radius, offset, 3);
            };
        }

        private FeatureSize parseFeatureSize(JsonObject obj) {
            int minClippedHeight = obj.has("min_clipped_height") ? obj.get("min_clipped_height").getAsInt() : -1;
            return switch (obj.get("type").getAsString()) {
                case "minecraft:two_layers_feature_size" -> new TwoLayersFeatureSize(
                    obj.get("limit").getAsInt(),
                    obj.get("lower_size").getAsInt(),
                    obj.get("upper_size").getAsInt(),
                    minClippedHeight);
                case "minecraft:three_layers_feature_size" -> new ThreeLayersFeatureSize(
                    obj.get("limit").getAsInt(),
                    obj.get("upper_limit").getAsInt(),
                    obj.get("lower_size").getAsInt(),
                    obj.get("middle_size").getAsInt(),
                    obj.get("upper_size").getAsInt(),
                    minClippedHeight);
                default -> new TwoLayersFeatureSize(1, 0, 1, minClippedHeight);
            };
        }

        private List<TreeDecorator> parseTreeDecorators(JsonArray arr) {
            if (arr == null || arr.isEmpty()) return List.of();
            ArrayList<TreeDecorator> decorators = new ArrayList<>();
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                switch (obj.get("type").getAsString()) {
                    case "minecraft:beehive" -> decorators.add(new BeehiveDecorator(obj.get("probability").getAsFloat()));
                    case "minecraft:cocoa" -> decorators.add(new CocoaDecorator(obj.get("probability").getAsFloat()));
                    case "minecraft:trunk_vine" -> decorators.add(new TrunkVineDecorator());
                    case "minecraft:leave_vine" -> decorators.add(new LeaveVineDecorator(obj.get("probability").getAsFloat()));
                    default -> {
                    }
                }
            }
            return List.copyOf(decorators);
        }

        private TreeIntProvider treeIntProvider(JsonElement el, int fallback) {
            if (el == null || el.isJsonNull()) return new TreeIntProvider.Constant(fallback);
            if (el.isJsonPrimitive()) return new TreeIntProvider.Constant(el.getAsInt());
            JsonObject obj = el.getAsJsonObject();
            String type = obj.has("type") ? obj.get("type").getAsString() : "";
            if (obj.has("value")) return new TreeIntProvider.Constant(obj.get("value").getAsInt());
            if ("minecraft:uniform".equals(type) || (obj.has("min_inclusive") && obj.has("max_inclusive"))) {
                return new TreeIntProvider.Uniform(obj.get("min_inclusive").getAsInt(), obj.get("max_inclusive").getAsInt());
            }
            if (obj.has("min_inclusive")) return new TreeIntProvider.Constant(obj.get("min_inclusive").getAsInt());
            return new TreeIntProvider.Constant(fallback);
        }

        private List<Integer> parseTargetPredicate(JsonObject target) {
            String type = target.get("predicate_type").getAsString();
            if (type.equals("minecraft:block_match")) {
                return List.of(registry.stateIdOrAir(blockName(target.get("block"))));
            }
            if (type.equals("minecraft:tag_match")) {
                return tagStates(target.get("tag").getAsString());
            }
            if (type.equals("minecraft:always_true")) {
                return List.of();
            }
            return List.of();
        }

        private Set<Integer> parseTagReference(JsonElement el) {
            if (el == null || el.isJsonNull() || !el.isJsonPrimitive()) return Set.of();
            String raw = el.getAsString();
            if (!raw.startsWith("#")) return Set.copyOf(registry.stateIdsForBlock(raw));
            return Set.copyOf(tagStates(raw.substring(1)));
        }

        private GeodeFeature.IntRange intRange(JsonObject provider) {
            return new GeodeFeature.IntRange(
                provider.get("min_inclusive").getAsInt(),
                provider.get("max_inclusive").getAsInt());
        }

        private int parseState(JsonObject state) {
            String name = state.get("Name").getAsString();
            if (!state.has("Properties")) {
                return registry.stateIdOrAir(name);
            }
            JsonObject props = state.getAsJsonObject("Properties");
            java.util.TreeMap<String, String> map = new java.util.TreeMap<>();
            for (var e : props.entrySet()) map.put(e.getKey(), e.getValue().getAsString());
            return registry.stateIdOrAir(name, map);
        }

        private int parseStateProvider(JsonObject provider) {
            JsonObject state = provider.getAsJsonObject("state");
            if (state != null) return parseState(state);
            JsonObject fallback = provider.getAsJsonObject("fallback");
            if (fallback != null) return parseStateProvider(fallback);
            return 0;
        }

        private static String blockName(JsonElement el) {
            if (el.isJsonPrimitive()) return el.getAsString();
            return el.getAsJsonObject().get("Name").getAsString();
        }

        private Set<Integer> parseTargetBlocks(JsonObject target) {
            if (target == null) return Set.of();
            if (target.has("blocks")) return parseBlockSet(target.get("blocks"));
            if (target.has("block")) return parseBlockSet(target.get("block"));
            return Set.of();
        }

        private Set<Integer> parseBlockSet(JsonElement el) {
            if (el == null || el.isJsonNull()) return Set.of();
            HashSet<Integer> ids = new HashSet<>();
            if (el.isJsonArray()) {
                for (JsonElement entry : el.getAsJsonArray()) {
                    ids.add(registry.stateIdOrAir(blockName(entry)));
                }
                return ids;
            }
            ids.add(registry.stateIdOrAir(blockName(el)));
            return ids;
        }

        // matching_blocks/matching_fluids predicate만 지원. 그외 타입은 null → modifier 생략(통과). random 미소비라 스트림 영향 없음.
        // matching_fluids: Furnace는 waterlogged 블록에 별도 fluid 레이어를 두지 않고 water를 단일 블록 상태로
        // 등록하므로(blocks/1.21.1.txt: minecraft:water 단일 id), fluid 검사를 블록 상태 검사로 근사한다 —
        // disk_sand/disk_clay/disk_gravel이 이 필터로 요구하는 "표면이 물"인지 확인에는 충분하다.
        private Set<Integer> blockPredicateStates(JsonObject predicate) {
            if (predicate == null) return null;
            String type = predicate.get("type").getAsString();
            if (type.equals("minecraft:matching_blocks")) {
                return parseBlockSet(predicate.get("blocks"));
            }
            if (type.equals("minecraft:matching_fluids")) {
                return parseBlockSet(predicate.get("fluids"));
            }
            return null;
        }

        private static int intProviderValue(JsonElement el, int fallback) {
            if (el == null || el.isJsonNull()) return fallback;
            if (el.isJsonPrimitive()) return el.getAsInt();
            JsonObject obj = el.getAsJsonObject();
            if (obj.has("value")) return obj.get("value").getAsInt();
            if (obj.has("min_inclusive")) return obj.get("min_inclusive").getAsInt();
            return fallback;
        }

        private List<Integer> tagStates(String tag) {
            return switch (tag) {
                case "minecraft:base_stone_overworld" -> states(
                    "minecraft:stone", "minecraft:granite", "minecraft:diorite",
                    "minecraft:andesite", "minecraft:tuff", "minecraft:deepslate");
                case "minecraft:stone_ore_replaceables" -> states(
                    "minecraft:stone", "minecraft:granite", "minecraft:diorite",
                    "minecraft:andesite");
                case "minecraft:deepslate_ore_replaceables" -> states("minecraft:deepslate", "minecraft:tuff");
                case "minecraft:features_cannot_replace" -> states(
                    "minecraft:bedrock", "minecraft:spawner", "minecraft:chest",
                    "minecraft:end_portal_frame", "minecraft:reinforced_deepslate",
                    "minecraft:trial_spawner", "minecraft:vault");
                case "minecraft:geode_invalid_blocks" -> states(
                    "minecraft:bedrock", "minecraft:water", "minecraft:lava",
                    "minecraft:ice", "minecraft:packed_ice", "minecraft:blue_ice");
                default -> states("minecraft:stone", "minecraft:deepslate");
            };
        }

        private List<Integer> states(String... names) {
            ArrayList<Integer> ids = new ArrayList<>(names.length);
            for (String name : names) ids.add(registry.stateIdOrAir(name));
            return ids;
        }

        private void parsePlacement(JsonObject json, List<PlacementModifier> out) {
            String type = json.get("type").getAsString();
            switch (type) {
                case "minecraft:count" -> out.add(countPlacement(json.get("count")));
                case "minecraft:rarity_filter" -> out.add(new PlacementModifier.RarityFilterPlacement(json.get("chance").getAsInt()));
                case "minecraft:in_square" -> out.add(new PlacementModifier.InSquarePlacement());
                case "minecraft:height_range" -> out.add(new PlacementModifier.HeightRangePlacement(heightProvider(json.getAsJsonObject("height"))));
                case "minecraft:biome" -> out.add(new PlacementModifier.BiomeFilter());
                case "minecraft:heightmap" -> out.add(new PlacementModifier.HeightmapPlacement(
                    parseHeightmapType(json)));
                case "minecraft:surface_water_depth_filter" -> out.add(
                    new PlacementModifier.SurfaceWaterDepthFilterPlacement(json.get("max_water_depth").getAsInt()));
                case "minecraft:noise_threshold_count" -> out.add(new PlacementModifier.NoiseThresholdCountPlacement(
                    json.get("noise_level").getAsDouble(),
                    json.get("below_noise").getAsInt(),
                    json.get("above_noise").getAsInt()));
                case "minecraft:noise_based_count" -> out.add(new PlacementModifier.NoiseBasedCountPlacement(
                    json.get("noise_to_count_ratio").getAsInt(),
                    json.get("noise_factor").getAsDouble(),
                    json.has("noise_offset") ? json.get("noise_offset").getAsDouble() : 0.0));
                case "minecraft:block_predicate_filter" -> {
                    JsonObject predicate = json.getAsJsonObject("predicate");
                    Set<Integer> allowed = blockPredicateStates(predicate);
                    if (allowed != null) out.add(new PlacementModifier.BlockPredicateFilter(allowed));
                    if (predicate != null && "minecraft:would_survive".equals(predicate.get("type").getAsString())) {
                        out.add(new PlacementModifier.WouldSurviveFilter(plantGround()));
                    }
                }
                case "minecraft:carving_mask" -> out.add(new PlacementModifier.CarvingMaskPlacement(
                    json.has("step") ? json.get("step").getAsString() : "air"));
                case "minecraft:surface_relative_threshold_filter" -> {
                    String hmStr = json.has("heightmap") ? json.get("heightmap").getAsString() : "WORLD_SURFACE_WG";
                    int minRel = json.has("min_inclusive") ? json.get("min_inclusive").getAsInt() : Integer.MIN_VALUE;
                    int maxRel = json.has("max_inclusive") ? json.get("max_inclusive").getAsInt() : Integer.MAX_VALUE;
                    out.add(new PlacementModifier.SurfaceRelativeThresholdFilter(parseHeightmapType(json), minRel, maxRel));
                }
                default -> {
                    // Unsupported modifiers are intentionally ignored until their feature type is implemented.
                }
            }
        }

        private static PlacementModifier countPlacement(JsonElement el) {
            if (el.isJsonPrimitive()) return new PlacementModifier.CountPlacement(el.getAsInt());
            JsonObject obj = el.getAsJsonObject();
            if ("minecraft:weighted_list".equals(obj.has("type") ? obj.get("type").getAsString() : "")) {
                JsonArray distribution = obj.getAsJsonArray("distribution");
                int[] min = new int[distribution.size()];
                int[] max = new int[distribution.size()];
                int[] weights = new int[distribution.size()];
                for (int i = 0; i < distribution.size(); i++) {
                    JsonObject entry = distribution.get(i).getAsJsonObject();
                    JsonElement data = entry.get("data");
                    if (data.isJsonPrimitive()) {
                        min[i] = data.getAsInt();
                        max[i] = data.getAsInt();
                    } else {
                        JsonObject provider = data.getAsJsonObject();
                        min[i] = provider.has("min_inclusive") ? provider.get("min_inclusive").getAsInt() : countValue(data);
                        max[i] = provider.has("max_inclusive") ? provider.get("max_inclusive").getAsInt() : min[i];
                    }
                    weights[i] = entry.get("weight").getAsInt();
                }
                return new PlacementModifier.WeightedListCountPlacement(min, max, weights);
            }
            if ("minecraft:uniform".equals(obj.has("type") ? obj.get("type").getAsString() : "")) {
                return new PlacementModifier.UniformCountPlacement(
                    obj.get("min_inclusive").getAsInt(),
                    obj.get("max_inclusive").getAsInt());
            }
            return new PlacementModifier.CountPlacement(countValue(el));
        }

        private static int countValue(JsonElement el) {
            if (el.isJsonPrimitive()) return el.getAsInt();
            JsonObject obj = el.getAsJsonObject();
            return obj.has("value") ? obj.get("value").getAsInt() : 1;
        }

        private static PlacementModifier.HeightProvider heightProvider(JsonObject json) {
            String type = json.get("type").getAsString();
            int min = PlacementModifier.worldgenYOrDefault(json.get("min_inclusive"), 0);
            int max = PlacementModifier.worldgenYOrDefault(json.get("max_inclusive"), 0);
            return switch (type) {
                case "minecraft:uniform" -> new PlacementModifier.UniformHeight(min, max);
                case "minecraft:biased_to_bottom" -> new PlacementModifier.BiasedToBottomHeight(min, max,
                    json.has("inner") ? json.get("inner").getAsInt() : 1);
                case "minecraft:very_biased_to_bottom" -> new PlacementModifier.VeryBiasedToBottomHeight(min, max,
                    json.has("inner") ? json.get("inner").getAsInt() : 1);
                case "minecraft:trapezoid" -> new PlacementModifier.TrapezoidHeight(min, max,
                    json.has("plateau") ? json.get("plateau").getAsInt() : 0);
                case "minecraft:constant" -> new PlacementModifier.ConstantHeight(
                    PlacementModifier.worldgenYOrDefault(json.get("value"), min));
                default -> new PlacementModifier.UniformHeight(min, max);
            };
        }

        private static PlacementModifier.HeightmapPlacement.Type parseHeightmapType(JsonObject json) {
            String name = json.has("heightmap") ? json.get("heightmap").getAsString() : "WORLD_SURFACE_WG";
            return switch (name) {
                case "OCEAN_FLOOR" -> PlacementModifier.HeightmapPlacement.Type.OCEAN_FLOOR;
                case "OCEAN_FLOOR_WG" -> PlacementModifier.HeightmapPlacement.Type.OCEAN_FLOOR_WG;
                case "MOTION_BLOCKING" -> PlacementModifier.HeightmapPlacement.Type.MOTION_BLOCKING;
                case "MOTION_BLOCKING_NO_LEAVES" -> PlacementModifier.HeightmapPlacement.Type.MOTION_BLOCKING_NO_LEAVES;
                default -> PlacementModifier.HeightmapPlacement.Type.WORLD_SURFACE_WG;
            };
        }
    }
}
