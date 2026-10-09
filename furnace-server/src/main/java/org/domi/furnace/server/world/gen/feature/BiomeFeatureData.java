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
import org.domi.furnace.server.world.gen.feature.foliage.BushFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.CherryFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.DarkOakFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.FancyFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.FoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.JungleFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.MegaPineFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.PineFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.RandomSpreadFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.foliage.SpruceFoliagePlacer;
import org.domi.furnace.server.world.gen.feature.size.FeatureSize;
import org.domi.furnace.server.world.gen.feature.size.ThreeLayersFeatureSize;
import org.domi.furnace.server.world.gen.feature.size.TwoLayersFeatureSize;
import org.domi.furnace.server.world.gen.feature.decorator.AlterGroundDecorator;
import org.domi.furnace.server.world.gen.feature.decorator.AttachedToLeavesDecorator;
import org.domi.furnace.server.world.gen.feature.root.MangroveRootPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.BendingTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.CherryTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.DarkOakTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.FancyTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.ForkingTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.GiantTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.MegaJungleTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.StraightTrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.TrunkPlacer;
import org.domi.furnace.server.world.gen.feature.trunk.UpwardsBranchingTrunkPlacer;

public final class BiomeFeatureData {

    public static final int STEP_COUNT = 11;

    // parseConfigured가 실제로 처리하는 configured_feature 루트 타입 — NoopFeatureAuditTest가 참조한다.
    // parseConfigured의 if-chain과 반드시 동기화(타입 추가/제거 시 여기도 갱신).
    public static final Set<String> HANDLED_CONFIGURED_FEATURE_TYPES = Set.of(
        "minecraft:ore", "minecraft:scattered_ore", "minecraft:spring_feature", "minecraft:disk",
        "minecraft:random_patch", "minecraft:flower", "minecraft:no_bonemeal_flower", "minecraft:simple_block", "minecraft:seagrass",
        "minecraft:kelp", "minecraft:freeze_top_layer", "minecraft:geode", "minecraft:tree",
        "minecraft:random_selector", "minecraft:random_boolean_selector", "minecraft:simple_random_selector",
        "minecraft:underwater_magma", "minecraft:multiface_growth", "minecraft:forest_rock", "minecraft:vines",
        "minecraft:vegetation_patch", "minecraft:waterlogged_vegetation_patch", "minecraft:block_column",
        "minecraft:huge_red_mushroom", "minecraft:huge_brown_mushroom", "minecraft:bamboo", "minecraft:ice_spike", "minecraft:iceberg", "minecraft:blue_ice",
        "minecraft:dripstone_cluster", "minecraft:large_dripstone", "minecraft:pointed_dripstone", "minecraft:monster_room", "minecraft:lake", "minecraft:desert_well",
        "minecraft:coral_tree", "minecraft:coral_claw", "minecraft:coral_mushroom", "minecraft:sea_pickle", "minecraft:block_pile", "minecraft:fossil", "minecraft:root_system",
        "minecraft:sculk_patch",
        "minecraft:huge_fungus", "minecraft:nether_forest_vegetation", "minecraft:twisting_vines", "minecraft:weeping_vines",
        "minecraft:netherrack_replace_blobs", "minecraft:basalt_columns", "minecraft:basalt_pillar", "minecraft:delta_feature",
        "minecraft:glowstone_blob"
    );

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
        return load(data, biomeIds, registryOrder, registry, -64, 384);
    }

    /**
     * @param genMinY  WorldGenerationContext.getMinGenY — max(차원 최저 y, 생성기 min_y)
     * @param genDepth WorldGenerationContext.getGenDepth — min(차원 높이, 생성기 noise 높이). 네더는 0/128.
     */
    public static BiomeFeatureData load(WorldgenData data, Iterable<String> biomeIds,
                                        List<String> registryOrder, BlockRegistry registry, int genMinY, int genDepth) {
        return load(data, biomeIds, registryOrder, registry, genMinY, genDepth, 63);
    }

    /** @param seaLevel ChunkGenerator.getSeaLevel — 노이즈 설정의 sea_level(오버월드 63, 네더 32). basalt_columns 가 쓴다. */
    public static BiomeFeatureData load(WorldgenData data, Iterable<String> biomeIds,
                                        List<String> registryOrder, BlockRegistry registry, int genMinY, int genDepth, int seaLevel) {
        // biome id별 base 온도 — FreezeTopLayer 동결 게이트용. FreezeTopLayer가 조회에 쓰는
        // FeatureWorldView.getBiomeId는 REGISTRY 순서 인덱스를 돌려주므로 registryOrder로 인덱싱해야 한다.
        // 이전에는 biomeIds(=possibleBiomes, biome_parameters 첫등장 순서)로 인덱싱해 온도가 서로 다른
        // biome에 매핑됐다(순수 jungle 청크에 눈이 쌓이는 실측 증상 — 냉대 biome이 있는 green fixture가
        // 없어 미검출이던 잠재 버그, mega-tree-placers task에서 수정).
        // 온도 데이터 없는 픽스처(worldgen/test 등)면 빈 배열 → 기본 0.5(동결 안 함)로 폴백.
        float[] biomeTemps;
        try {
            Iterable<String> tempOrder = registryOrder != null ? registryOrder : biomeIds;
            ArrayList<Float> temps = new ArrayList<>();
            for (String biomeId : tempOrder) temps.add(data.biomeTemperature(biomeId));
            biomeTemps = new float[temps.size()];
            for (int i = 0; i < biomeTemps.length; i++) biomeTemps[i] = temps.get(i);
        } catch (IllegalStateException e) {
            biomeTemps = new float[0];
        }
        boolean[] biomeFrozen = new boolean[biomeTemps.length];
        if (biomeTemps.length > 0) {
            int fi = 0;
            for (String biomeId : (registryOrder != null ? registryOrder : biomeIds)) {
                biomeFrozen[fi++] = data.biomeFrozenModifier(biomeId);
            }
        }

        Loader loader = new Loader(data, registry, biomeTemps, biomeFrozen, genMinY, genDepth, seaLevel);
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

        BiomeFeatureData result = new BiomeFeatureData(loaded, idByFeature, frozenBiomeIds);
        result.loader = loader;
        return result;
    }

    private Loader loader;

    /** id 로 placed feature 를 얻는다(구조물 jigsaw feature_pool_element 용). 미지원이면 null. */
    public PlacedFeature placedFeatureById(String id) {
        if (loader == null) return null;
        synchronized (loader) {
            return loader.placedFeature(id.contains(":") ? id : "minecraft:" + id);
        }
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
        private final boolean[] biomeFrozen;
        private final Map<String, PlacedFeature> placedCache = new HashMap<>();
        private final Map<String, ConfiguredFeature> configuredCache = new HashMap<>();

        private final int genMinY, genDepth, seaLevel;

        Loader(WorldgenData data, BlockRegistry registry, float[] biomeTemps, boolean[] biomeFrozen, int genMinY, int genDepth, int seaLevel) {
            this.seaLevel = seaLevel;
            this.genMinY = genMinY;
            this.genDepth = genDepth;
            this.data = data;
            this.registry = registry;
            this.biomeTemps = biomeTemps;
            this.biomeFrozen = biomeFrozen;
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
            if (type.equals("minecraft:random_patch") || type.equals("minecraft:flower") || type.equals("minecraft:no_bonemeal_flower")) {
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
                return new FreezeTopLayerFeature(registry, biomeTemps, biomeFrozen);
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
            if (type.equals("minecraft:sculk_patch")) {
                return new SculkPatchFeature(sculkBlocks(), config.get("charge_count").getAsInt(), config.get("amount_per_charge").getAsInt(),
                    config.get("spread_attempts").getAsInt(), config.get("growth_rounds").getAsInt(), config.get("spread_rounds").getAsInt(),
                    treeIntProvider(config.get("extra_rare_growths"), 0), config.get("catalyst_chance").getAsFloat());
            }
            if (type.equals("minecraft:forest_rock")) {
                return new ForestRockFeature(
                    parseState(config.getAsJsonObject("state")),
                    buildTreeDirtStates(),
                    Set.copyOf(states(
                        "minecraft:stone", "minecraft:granite", "minecraft:diorite",
                        "minecraft:andesite", "minecraft:tuff", "minecraft:deepslate")));
            }
            if (type.equals("minecraft:vines")) {
                return parseVines();
            }
            if (type.equals("minecraft:huge_red_mushroom") || type.equals("minecraft:huge_brown_mushroom")) {
                StateProvider cap = parseToPlaceProvider(config.getAsJsonObject("cap_provider"));
                StateProvider stem = parseToPlaceProvider(config.getAsJsonObject("stem_provider"));
                if (cap == null || stem == null) return NoopFeature.INSTANCE;
                return new HugeMushroomFeature(
                    type.equals("minecraft:huge_red_mushroom") ? HugeMushroomFeature.Kind.RED : HugeMushroomFeature.Kind.BROWN,
                    cap, stem, config.has("foliage_radius") ? config.get("foliage_radius").getAsInt() : 2, registry);
            }
            if (type.equals("minecraft:dripstone_cluster")) {
                return new DripstoneFeatures.Cluster(dripstone(),
                    config.get("floor_to_ceiling_search_range").getAsInt(),
                    treeIntProvider(config.get("height"), 1), treeIntProvider(config.get("radius"), 1),
                    config.get("max_stalagmite_stalactite_height_diff").getAsInt(),
                    config.get("height_deviation").getAsInt(),
                    treeIntProvider(config.get("dripstone_block_layer_thickness"), 0),
                    FeatureFloatProvider.fromJson(config.get("density")), FeatureFloatProvider.fromJson(config.get("wetness")),
                    config.get("chance_of_dripstone_column_at_max_distance_from_center").getAsFloat(),
                    config.get("max_distance_from_edge_affecting_chance_of_dripstone_column").getAsInt(),
                    config.get("max_distance_from_center_affecting_height_bias").getAsInt());
            }
            if (type.equals("minecraft:large_dripstone")) {
                TreeIntProvider radius = treeIntProvider(config.get("column_radius"), 1);
                if (!(radius instanceof TreeIntProvider.Uniform uniform)) {
                    throw new IllegalArgumentException("large_dripstone column_radius는 uniform만 지원: " + config.get("column_radius"));
                }
                return new DripstoneFeatures.Large(dripstone(),
                    config.has("floor_to_ceiling_search_range") ? config.get("floor_to_ceiling_search_range").getAsInt() : 30,
                    uniform, FeatureFloatProvider.fromJson(config.get("height_scale")),
                    config.get("max_column_radius_to_cave_height_ratio").getAsFloat(),
                    FeatureFloatProvider.fromJson(config.get("stalactite_bluntness")),
                    FeatureFloatProvider.fromJson(config.get("stalagmite_bluntness")),
                    FeatureFloatProvider.fromJson(config.get("wind_speed")),
                    config.get("min_radius_for_wind").getAsInt(), config.get("min_bluntness_for_wind").getAsFloat());
            }
            if (type.equals("minecraft:pointed_dripstone")) {
                return new DripstoneFeatures.Pointed(dripstone(),
                    config.has("chance_of_taller_dripstone") ? config.get("chance_of_taller_dripstone").getAsFloat() : 0.2F,
                    config.has("chance_of_directional_spread") ? config.get("chance_of_directional_spread").getAsFloat() : 0.7F,
                    config.has("chance_of_spread_radius2") ? config.get("chance_of_spread_radius2").getAsFloat() : 0.5F,
                    config.has("chance_of_spread_radius3") ? config.get("chance_of_spread_radius3").getAsFloat() : 0.5F);
            }
            if (type.equals("minecraft:blue_ice")) {
                return new BlueIceFeature(registry);
            }
            if (type.equals("minecraft:iceberg")) {
                return new IcebergFeature(parseState(config.getAsJsonObject("state")), registry);
            }
            if (type.equals("minecraft:ice_spike")) {
                return new IceSpikeFeature(registry);
            }
            if (type.equals("minecraft:monster_room")) {
                return new MonsterRoomFeature(registry);
            }
            if (type.equals("minecraft:coral_tree")) {
                return CoralFeature.of(CoralFeature.Shape.TREE, registry);
            }
            if (type.equals("minecraft:coral_claw")) {
                return CoralFeature.of(CoralFeature.Shape.CLAW, registry);
            }
            if (type.equals("minecraft:coral_mushroom")) {
                return CoralFeature.of(CoralFeature.Shape.MUSHROOM, registry);
            }
            if (type.equals("minecraft:sea_pickle")) {
                return new SeaPickleFeature(treeIntProvider(config.get("count"), 20), registry);
            }
            if (type.equals("minecraft:desert_well")) {
                return new DesertWellFeature(registry);
            }
            if (type.equals("minecraft:root_system")) {
                PlacedFeature tree;
                try {
                    tree = parseInlinePlacedFeature(config.get("feature"));
                } catch (RuntimeException e) {
                    return NoopFeature.INSTANCE;   // 진달래 나무(가중 잎·bending 줄기) 미지원 — 거짓 parity 방지
                }
                StateProvider rootSp = parseToPlaceProvider(config.getAsJsonObject("root_state_provider"));
                StateProvider hangSp = parseToPlaceProvider(config.getAsJsonObject("hanging_root_state_provider"));
                if (tree == null || tree.feature() instanceof NoopFeature || rootSp == null || hangSp == null) return NoopFeature.INSTANCE;
                String rr = config.get("root_replaceable").getAsString();
                return new RootSystemFeature(tree, config.get("required_vertical_space_for_tree").getAsInt(), config.get("root_radius").getAsInt(),
                    Set.copyOf(tagStates(rr.substring(1))), rootSp, config.get("root_placement_attempts").getAsInt(),
                    config.get("root_column_max_height").getAsInt(), config.get("hanging_root_radius").getAsInt(),
                    config.get("hanging_roots_vertical_span").getAsInt(), hangSp, config.get("hanging_root_placement_attempts").getAsInt(),
                    config.get("allowed_vertical_water_for_tree").getAsInt(), predicates().parse(config.getAsJsonObject("allowed_tree_position")), registry);
            }
            if (type.equals("minecraft:fossil")) {
                java.util.List<String> fs = new ArrayList<>(), os = new ArrayList<>();
                for (JsonElement e : config.getAsJsonArray("fossil_structures")) fs.add(e.getAsString());
                for (JsonElement e : config.getAsJsonArray("overlay_structures")) os.add(e.getAsString());
                return new FossilFeature(registry, fs, os, config.get("fossil_processors").getAsString(),
                    config.get("overlay_processors").getAsString(), config.get("max_empty_corners_allowed").getAsInt());
            }
            if (type.equals("minecraft:block_pile")) {
                StateProvider sp = parseToPlaceProvider(config.getAsJsonObject("state_provider"));
                return sp == null ? NoopFeature.INSTANCE : new BlockPileFeature(sp, registry);
            }
            if (type.equals("minecraft:lake")) {
                return new LakeFeature(parseState(config.getAsJsonObject("fluid").getAsJsonObject("state")),
                    parseState(config.getAsJsonObject("barrier").getAsJsonObject("state")), registry);
            }
            if (type.equals("minecraft:huge_fungus")) {
                return new HugeFungusFeature(
                    parseState(config.getAsJsonObject("valid_base_block")), parseState(config.getAsJsonObject("stem_state")),
                    parseState(config.getAsJsonObject("hat_state")), parseState(config.getAsJsonObject("decor_state")),
                    predicates().parse(config.getAsJsonObject("replaceable_blocks")),
                    config.has("planted") && config.get("planted").getAsBoolean(), genDepth, registry);
            }
            if (type.equals("minecraft:nether_forest_vegetation")) {
                StateProvider provider = parseToPlaceProvider(config.getAsJsonObject("state_provider"));
                if (provider == null) return NoopFeature.INSTANCE;
                return new NetherForestVegetationFeature(provider, config.get("spread_width").getAsInt(),
                    config.get("spread_height").getAsInt(), registry, survival());
            }
            if (type.equals("minecraft:twisting_vines")) {
                return new NetherVinesFeatures.Twisting(new NetherVinesFeatures.Blocks(registry), config.get("spread_width").getAsInt(),
                    config.get("spread_height").getAsInt(), config.get("max_height").getAsInt());
            }
            if (type.equals("minecraft:weeping_vines")) {
                return new NetherVinesFeatures.Weeping(new NetherVinesFeatures.Blocks(registry));
            }
            if (type.equals("minecraft:netherrack_replace_blobs")) {
                return new NetherrackReplaceBlobsFeature(parseState(config.getAsJsonObject("target")),
                    parseState(config.getAsJsonObject("state")), treeIntProvider(config.get("radius"), 0), registry);
            }
            if (type.equals("minecraft:basalt_columns")) {
                return new BasaltFeatures.Columns(treeIntProvider(config.get("reach"), 0), treeIntProvider(config.get("height"), 0), seaLevel, registry);
            }
            if (type.equals("minecraft:basalt_pillar")) {
                return new BasaltFeatures.Pillar(registry);
            }
            if (type.equals("minecraft:delta_feature")) {
                return new DeltaFeature(parseState(config.getAsJsonObject("contents")), parseState(config.getAsJsonObject("rim")),
                    treeIntProvider(config.get("size"), 0), treeIntProvider(config.get("rim_size"), 0), registry);
            }
            if (type.equals("minecraft:glowstone_blob")) {
                return new GlowstoneBlobFeature(registry);
            }
            if (type.equals("minecraft:bamboo")) {
                return new BambooFeature(config.get("probability").getAsFloat(), registry);
            }
            if (type.equals("minecraft:block_column")) {
                return parseBlockColumn(config);
            }
            if (type.equals("minecraft:vegetation_patch") || type.equals("minecraft:waterlogged_vegetation_patch")) {
                return parseVegetationPatch(config, type.equals("minecraft:waterlogged_vegetation_patch"));
            }
            // 미구현 feature 타입 → placeholder. index parity 유지(Phase 2에서 실제 구현으로 교체).
            return NoopFeature.INSTANCE;
        }

        private ConfiguredFeature parseBlockColumn(JsonObject config) {
            ArrayList<BlockColumnFeature.Layer> layers = new ArrayList<>();
            for (JsonElement el : config.getAsJsonArray("layers")) {
                JsonObject layer = el.getAsJsonObject();
                StateProvider provider = parseToPlaceProvider(layer.getAsJsonObject("provider"));
                if (provider == null) return NoopFeature.INSTANCE;
                layers.add(new BlockColumnFeature.Layer(treeIntProvider(layer.get("height"), 0), provider));
            }
            return new BlockColumnFeature(layers, direction(config.get("direction").getAsString()),
                predicates().parse(config.getAsJsonObject("allowed_placement")),
                config.has("prioritize_tip") && config.get("prioritize_tip").getAsBoolean());
        }

        private ConfiguredFeature parseVegetationPatch(JsonObject config, boolean waterlogged) {
            StateProvider ground = parseToPlaceProvider(config.getAsJsonObject("ground_state"));
            if (ground == null) return NoopFeature.INSTANCE;   // 미지원 state provider — 거짓 parity 방지
            String replaceable = config.get("replaceable").getAsString();
            Set<Integer> replaceableStates = Set.copyOf(tagStates(replaceable.substring(1)));
            PlacedFeature vegetation = parseInlinePlacedFeature(config.get("vegetation_feature"));
            VegetationPatchFeature.Surface surface = "ceiling".equals(config.get("surface").getAsString())
                ? VegetationPatchFeature.Surface.CEILING : VegetationPatchFeature.Surface.FLOOR;
            return new VegetationPatchFeature(replaceableStates, ground, vegetation, surface,
                treeIntProvider(config.get("depth"), 1),
                config.get("extra_bottom_block_chance").getAsFloat(),
                config.get("vertical_range").getAsInt(),
                config.get("vegetation_chance").getAsFloat(),
                treeIntProvider(config.get("xz_radius"), 1),
                config.get("extra_edge_column_chance").getAsFloat(),
                waterlogged, registry.stateIdOrAir("minecraft:water"), registry);
        }

        private ConfiguredFeature parseRandomPatch(JsonObject config) {
            // config.feature = 인라인 placed feature {feature: configured, placement: [...]}
            JsonObject inlinePlaced = config.getAsJsonObject("feature");
            ConfiguredFeature inner = parseConfigured(inlinePlaced.getAsJsonObject("feature"));
            // 미지원 inner → placeholder 유지(거짓 parity 방지)
            if (inner instanceof NoopFeature) {
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
            // 바닐라는 to_place가 어떤 블록이든 canSurvive를 그 블록 규칙대로 적용한다 — 규칙 미구현 블록이 섞이면 거짓 parity 대신 placeholder.
            if (!allBlocksSupported(config.getAsJsonObject("to_place"))) return NoopFeature.INSTANCE;
            return new SimpleBlockFeature(provider, survival());
        }

        private boolean allBlocksSupported(JsonElement el) {
            if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("Name") && !survival().supportsBlock(o.get("Name").getAsString())) return false;
                for (var e : o.entrySet()) if (!allBlocksSupported(e.getValue())) return false;
            } else if (el.isJsonArray()) {
                for (JsonElement c : el.getAsJsonArray()) if (!allBlocksSupported(c)) return false;
            }
            return true;
        }

        private DripstoneFeatures.Support dripstoneSupport;

        private DripstoneFeatures.Support dripstone() {
            if (dripstoneSupport == null) dripstoneSupport = new DripstoneFeatures.Support(registry, new FluidKinds(registry));
            return dripstoneSupport;
        }

        private BlockSurvival survivalCache;

        private BlockSurvival survival() {
            if (survivalCache == null) survivalCache = new BlockSurvival(registry, new FluidKinds(registry));
            return survivalCache;
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
                case "minecraft:randomized_int_state_provider" -> {
                    StateProvider source = parseToPlaceProvider(toPlace.getAsJsonObject("source"));
                    if (source == null) return null;
                    return new RandomizedIntStateProvider(source, toPlace.get("property").getAsString(),
                        treeIntProvider(toPlace.get("values"), 0), registry);
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
                case "minecraft:rotated_block_provider" -> {
                    int base = parseState(toPlace.getAsJsonObject("state"));
                    String name = registry.nameOf(base);
                    return new BlockPileFeature.RotatedBlockProvider(
                        registry.stateIdOrAir(name, java.util.Map.of("axis", "x")),
                        registry.stateIdOrAir(name, java.util.Map.of("axis", "y")),
                        registry.stateIdOrAir(name, java.util.Map.of("axis", "z")));
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

        private MultifaceSpreader.Env multifaceEnv;
        private SculkPatchFeature.Blocks sculkBlocks;

        private MultifaceSpreader.Env multifaceEnv() {
            if (multifaceEnv == null) multifaceEnv = new MultifaceSpreader.Env(registry);
            return multifaceEnv;
        }

        private SculkPatchFeature.Blocks sculkBlocks() {
            if (sculkBlocks == null) sculkBlocks = new SculkPatchFeature.Blocks(multifaceEnv());
            return sculkBlocks;
        }

        private ConfiguredFeature parseMultifaceGrowth(JsonObject config) {
            String blockName = config.has("block") ? blockName(config.get("block")) : "minecraft:glow_lichen";
            if ("minecraft:sculk_vein".equals(blockName)) {
                boolean[] on = new boolean[registry.totalStateCount()];
                for (JsonElement el : config.getAsJsonArray("can_be_placed_on")) for (int id : parseTagReference(el)) on[id] = true;
                return new MultifaceGrowthFeature(sculkBlocks().veinSpreader,
                    config.has("search_range") ? config.get("search_range").getAsInt() : 10,
                    config.has("can_place_on_floor") && config.get("can_place_on_floor").getAsBoolean(),
                    config.has("can_place_on_ceiling") && config.get("can_place_on_ceiling").getAsBoolean(),
                    config.has("can_place_on_wall") && config.get("can_place_on_wall").getAsBoolean(),
                    config.has("chance_of_spreading") ? config.get("chance_of_spreading").getAsFloat() : 0.5f, on);
            }
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
            int fallbackId = parseState(fallback.getAsJsonObject("state"));
            java.util.List<DiskFeature.Rule> rules = new java.util.ArrayList<>();
            for (JsonElement rel : sp.has("rules") ? sp.getAsJsonArray("rules") : new JsonArray()) {
                JsonObject rule = rel.getAsJsonObject();
                JsonObject then = rule.getAsJsonObject("then");
                if (!"minecraft:simple_state_provider".equals(then.get("type").getAsString())) {
                    return NoopFeature.INSTANCE;
                }
                rules.add(new DiskFeature.Rule(
                    predicates().parse(rule.getAsJsonObject("if_true")),
                    parseState(then.getAsJsonObject("state"))));
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
            int[][][] weightedLeaves = null;
            int[] leafWeights = null;
            String leafName;
            if (foliageProvider.has("entries")) {   // weighted_state_provider
                JsonArray entries = foliageProvider.getAsJsonArray("entries");
                weightedLeaves = new int[entries.size()][][];
                leafWeights = new int[entries.size()];
                for (int i = 0; i < entries.size(); i++) {
                    JsonObject en = entries.get(i).getAsJsonObject();
                    weightedLeaves[i] = buildLeafStates(en.getAsJsonObject("data").get("Name").getAsString());
                    leafWeights[i] = en.get("weight").getAsInt();
                }
                leafName = entries.get(0).getAsJsonObject().getAsJsonObject("data").get("Name").getAsString();
            } else {
                leafName = foliageProvider.getAsJsonObject("state").get("Name").getAsString();
            }
            int dirtState = config.has("dirt_provider")
                ? parseStateProvider(config.getAsJsonObject("dirt_provider"))
                : registry.stateIdOrAir("minecraft:dirt");
            buildVineNonAttachable();
            return new TreeFeature(new TreeConfig(
                trunkState,
                dirtState,
                leafName,
                buildLeafStates(leafName),
                parseTrunkPlacer(config.getAsJsonObject("trunk_placer")),
                parseFoliagePlacer(config.getAsJsonObject("foliage_placer")),
                config.has("root_placer") ? parseRootPlacer(config.getAsJsonObject("root_placer")) : null,
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
                buildBeeNestState(),
                weightedLeaves,
                leafWeights));
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

        // VinesFeature용 부착 불가 블록 집합 — vanilla canAttachTo(지지면/충돌면 full 판정)의 블랙리스트 근사.
        // 잎(leaves)은 충돌면이 full이라 부착 가능하므로 포함하지 않는다.
        private ConfiguredFeature parseVines() {
            Set<Integer> nonAttachable = buildVineNonAttachable();
            Map<String, String> upProps = new LinkedHashMap<>(Map.of(
                "east", "false", "north", "false", "south", "false", "up", "false", "west", "false"));
            upProps.put("up", "true");
            return new VinesFeature(nonAttachable, buildVineStates(),
                registry.stateIdOrAir("minecraft:vine", upProps));
        }

        private Set<Integer> buildVineNonAttachable() {
            HashSet<Integer> nonAttachable = new HashSet<>();
            for (String name : new String[]{
                "minecraft:air", "minecraft:cave_air", "minecraft:void_air",
                "minecraft:water", "minecraft:lava",
                "minecraft:short_grass", "minecraft:fern", "minecraft:dead_bush", "minecraft:tall_grass",
                "minecraft:large_fern", "minecraft:sunflower", "minecraft:lilac", "minecraft:rose_bush",
                "minecraft:peony", "minecraft:seagrass", "minecraft:tall_seagrass",
                "minecraft:vine", "minecraft:glow_lichen", "minecraft:hanging_roots", "minecraft:pitcher_plant",
                "minecraft:dandelion", "minecraft:poppy", "minecraft:blue_orchid", "minecraft:allium",
                "minecraft:azure_bluet", "minecraft:red_tulip", "minecraft:orange_tulip", "minecraft:white_tulip",
                "minecraft:pink_tulip", "minecraft:oxeye_daisy", "minecraft:cornflower", "minecraft:lily_of_the_valley",
                "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:cocoa",
                "minecraft:melon_stem", "minecraft:pumpkin_stem", "minecraft:attached_melon_stem", "minecraft:attached_pumpkin_stem",
                "minecraft:lily_pad", "minecraft:sugar_cane", "minecraft:bamboo", "minecraft:bamboo_sapling",
                "minecraft:snow", "minecraft:cactus", "minecraft:moss_carpet", "minecraft:pink_petals",
                "minecraft:oak_sapling", "minecraft:spruce_sapling", "minecraft:birch_sapling", "minecraft:jungle_sapling",
                "minecraft:acacia_sapling", "minecraft:dark_oak_sapling", "minecraft:cherry_sapling", "minecraft:mangrove_propagule",
                "minecraft:sweet_berry_bush", "minecraft:torch", "minecraft:sea_pickle", "minecraft:spore_blossom",
                "minecraft:small_dripleaf", "minecraft:big_dripleaf", "minecraft:big_dripleaf_stem",
                "minecraft:cave_vines", "minecraft:cave_vines_plant", "minecraft:pointed_dripstone",
                "minecraft:azalea", "minecraft:flowering_azalea", "minecraft:sculk_vein"
            }) {
                nonAttachable.addAll(registry.stateIdsForBlock(name));
            }
            Set<Integer> frozen = Set.copyOf(nonAttachable);
            VineSupport.init(frozen);
            return frozen;
        }

        private int[] buildVineStates() {
            // vine의 canonical key는 east/north/south/up/west 5개 프로퍼티 전부를 요구한다 — 이전처럼 방향
            // 1개만 넣으면 exact 매치가 없어 default state(전부 false)로 폴백해 4방향 모두 같은 id가 됐다
            // (vine이 등장하는 green fixture가 없어 미검출이던 잠재 버그, mega-tree-placers task에서 수정).
            int[] ids = new int[4];
            for (TreeFeature.Direction dir : TreeFeature.HORIZONTAL_DIRECTIONS) {
                Map<String, String> props = new LinkedHashMap<>(Map.of(
                    "east", "false", "north", "false", "south", "false", "up", "false", "west", "false"));
                props.put(dir.propertyName(), "true");
                ids[dir.horizontalIndex()] = registry.stateIdOrAir("minecraft:vine", props);
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
                case "minecraft:fancy_trunk_placer" -> new FancyTrunkPlacer(baseHeight, randA, randB);
                case "minecraft:giant_trunk_placer" -> new GiantTrunkPlacer(baseHeight, randA, randB);
                case "minecraft:mega_jungle_trunk_placer" -> new MegaJungleTrunkPlacer(baseHeight, randA, randB);
                case "minecraft:upwards_branching_trunk_placer" -> new UpwardsBranchingTrunkPlacer(
                    baseHeight, randA, randB,
                    treeIntProvider(obj.get("extra_branch_steps"), 1),
                    obj.get("place_branch_per_log_probability").getAsFloat(),
                    treeIntProvider(obj.get("extra_branch_length"), 0));
                case "minecraft:cherry_trunk_placer" -> new CherryTrunkPlacer(
                    baseHeight, randA, randB,
                    treeIntProvider(obj.get("branch_count"), 1),
                    treeIntProvider(obj.get("branch_horizontal_length"), 2),
                    (TreeIntProvider.Uniform) treeIntProvider(obj.get("branch_start_offset_from_top"), -4),
                    treeIntProvider(obj.get("branch_end_offset_from_top"), -1));
                case "minecraft:bending_trunk_placer" -> new BendingTrunkPlacer(
                    baseHeight, randA, randB,
                    obj.has("min_height_for_leaves") ? obj.get("min_height_for_leaves").getAsInt() : 1,
                    treeIntProvider(obj.get("bend_length"), 1));
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
                case "minecraft:fancy_foliage_placer" -> new FancyFoliagePlacer(radius, offset, obj.get("height").getAsInt());
                case "minecraft:mega_pine_foliage_placer" -> new MegaPineFoliagePlacer(radius, offset, treeIntProvider(obj.get("crown_height"), 15));
                case "minecraft:jungle_foliage_placer" -> new JungleFoliagePlacer(radius, offset, obj.get("height").getAsInt());
                case "minecraft:cherry_foliage_placer" -> new CherryFoliagePlacer(
                    radius, offset, treeIntProvider(obj.get("height"), 5),
                    obj.get("wide_bottom_layer_hole_chance").getAsFloat(),
                    obj.get("corner_hole_chance").getAsFloat(),
                    obj.get("hanging_leaves_chance").getAsFloat(),
                    obj.get("hanging_leaves_extension_chance").getAsFloat());
                case "minecraft:random_spread_foliage_placer" -> new RandomSpreadFoliagePlacer(
                    radius, offset,
                    treeIntProvider(obj.get("foliage_height"), 2),
                    obj.get("leaf_placement_attempts").getAsInt());
                case "minecraft:bush_foliage_placer" -> new BushFoliagePlacer(radius, offset, obj.get("height").getAsInt());
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
                    case "minecraft:alter_ground" -> decorators.add(new AlterGroundDecorator(
                        parseStateProvider(obj.getAsJsonObject("provider"))));
                    case "minecraft:attached_to_leaves" -> decorators.add(parseAttachedToLeaves(obj));
                    default -> {
                    }
                }
            }
            return List.copyOf(decorators);
        }

        // attached_to_leaves의 block_provider는 mangrove 데이터에서 항상 randomized_int_state_provider
        // (source=mangrove_propagule, property=age, values=uniform) — age별 state id 배열로 전개한다.
        private AttachedToLeavesDecorator parseAttachedToLeaves(JsonObject obj) {
            JsonObject provider = obj.getAsJsonObject("block_provider");
            JsonObject sourceState = provider.getAsJsonObject("source").getAsJsonObject("state");
            String blockName = sourceState.get("Name").getAsString();
            String property = provider.get("property").getAsString();
            TreeIntProvider values = treeIntProvider(provider.get("values"), 0);

            Map<String, String> baseProps = new LinkedHashMap<>();
            if (sourceState.has("Properties")) {
                for (Map.Entry<String, JsonElement> entry : sourceState.getAsJsonObject("Properties").entrySet()) {
                    baseProps.put(entry.getKey(), entry.getValue().getAsString());
                }
            }
            // values의 최대치까지 state id를 전개 — mangrove age는 uniform 0..4.
            int maxValue = values instanceof TreeIntProvider.Uniform u ? u.maxInclusive() : 0;
            int[] stateIds = new int[maxValue + 1];
            for (int v = 0; v <= maxValue; v++) {
                baseProps.put(property, Integer.toString(v));
                stateIds[v] = registry.stateIdOrAir(blockName, Map.copyOf(baseProps));
            }

            JsonArray dirs = obj.getAsJsonArray("directions");
            TreeFeature.Direction[] directions = new TreeFeature.Direction[dirs.size()];
            for (int i = 0; i < dirs.size(); i++) {
                directions[i] = TreeFeature.Direction.valueOf(dirs.get(i).getAsString().toUpperCase(java.util.Locale.ROOT));
            }

            return new AttachedToLeavesDecorator(
                obj.get("probability").getAsFloat(),
                obj.get("exclusion_radius_xz").getAsInt(),
                obj.get("exclusion_radius_y").getAsInt(),
                obj.get("required_empty_blocks").getAsInt(),
                directions,
                values,
                stateIds);
        }

        private MangroveRootPlacer parseRootPlacer(JsonObject obj) {
            JsonObject mangrove = obj.getAsJsonObject("mangrove_root_placement");
            JsonObject above = obj.has("above_root_placement") ? obj.getAsJsonObject("above_root_placement") : null;

            HashSet<Integer> muddyIn = new HashSet<>();
            for (JsonElement el : mangrove.getAsJsonArray("muddy_roots_in")) {
                muddyIn.addAll(registry.stateIdsForBlock(el.getAsString()));
            }
            muddyIn.remove(0);

            return new MangroveRootPlacer(
                treeIntProvider(obj.get("trunk_offset_y"), 0),
                registry.stateIdOrAir("minecraft:mangrove_roots", Map.of("waterlogged", "false")),
                registry.stateIdOrAir("minecraft:mangrove_roots", Map.of("waterlogged", "true")),
                above != null ? above.get("above_root_placement_chance").getAsFloat() : 0.0f,
                above != null ? parseStateProvider(above.getAsJsonObject("above_root_provider")) : 0,
                mangrove.get("max_root_width").getAsInt(),
                mangrove.get("max_root_length").getAsInt(),
                mangrove.get("random_skew_chance").getAsFloat(),
                Set.copyOf(muddyIn),
                parseStateProvider(mangrove.getAsJsonObject("muddy_roots_provider")));
        }

        /** vanilla IntProvider 코덱: 정수면 constant, 객체면 type으로 분기. 알 수 없는 형태는 조용히 대체하지 않고 예외. */
        private TreeIntProvider treeIntProvider(JsonElement el, int fallback) {
            if (el == null || el.isJsonNull()) return new TreeIntProvider.Constant(fallback);
            if (el.isJsonPrimitive()) return new TreeIntProvider.Constant(el.getAsInt());
            JsonObject obj = el.getAsJsonObject();
            // vanilla IntProvider 코덱: type 생략 시 uniform으로 해석
            String type = obj.has("type") ? obj.get("type").getAsString() : "minecraft:uniform";
            switch (type) {
                case "minecraft:constant" -> {
                    return new TreeIntProvider.Constant(obj.get("value").getAsInt());
                }
                case "minecraft:uniform" -> {
                    return new TreeIntProvider.Uniform(obj.get("min_inclusive").getAsInt(), obj.get("max_inclusive").getAsInt());
                }
                case "minecraft:biased_to_bottom" -> {
                    return new TreeIntProvider.BiasedToBottom(obj.get("min_inclusive").getAsInt(), obj.get("max_inclusive").getAsInt());
                }
                case "minecraft:clamped" -> {
                    return new TreeIntProvider.Clamped(treeIntProvider(obj.get("source"), fallback),
                        obj.get("min_inclusive").getAsInt(), obj.get("max_inclusive").getAsInt());
                }
                case "minecraft:clamped_normal" -> {
                    return new TreeIntProvider.ClampedNormal(obj.get("mean").getAsFloat(), obj.get("deviation").getAsFloat(),
                        obj.get("min_inclusive").getAsInt(), obj.get("max_inclusive").getAsInt());
                }
                case "minecraft:weighted_list" -> {
                    ArrayList<TreeIntProvider.WeightedList.Entry> entries = new ArrayList<>();
                    for (JsonElement entryEl : obj.getAsJsonArray("distribution")) {
                        JsonObject entryObj = entryEl.getAsJsonObject();
                        entries.add(new TreeIntProvider.WeightedList.Entry(
                            entryObj.get("weight").getAsInt(), treeIntProvider(entryObj.get("data"), fallback)));
                    }
                    return new TreeIntProvider.WeightedList(List.copyOf(entries));
                }
                default -> throw new IllegalArgumentException("알 수 없는 IntProvider: " + obj);
            }
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
        /**
         * block_predicate 트리 파서 — matching_blocks(offset 지원)/not/all_of/any_of 조합만.
         * 지원하지 않는 하위 타입이 섞여 있으면 null(호출부에서 기존 폴백 경로 사용).
         */
        private PlacementModifier.BlockPred parseBlockPred(JsonObject predicate) {
            if (predicate == null) return null;
            String type = predicate.get("type").getAsString();
            switch (type) {
                case "minecraft:matching_blocks" -> {
                    Set<Integer> matchStates = parseBlockSet(predicate.get("blocks"));
                    if (matchStates == null) return null;
                    int ox = 0;
                    int oy = 0;
                    int oz = 0;
                    if (predicate.has("offset")) {
                        JsonArray off = predicate.getAsJsonArray("offset");
                        ox = off.get(0).getAsInt();
                        oy = off.get(1).getAsInt();
                        oz = off.get(2).getAsInt();
                    }
                    return new PlacementModifier.MatchingBlocksPred(Set.copyOf(matchStates), ox, oy, oz);
                }
                case "minecraft:not" -> {
                    PlacementModifier.BlockPred inner = parseBlockPred(predicate.getAsJsonObject("predicate"));
                    return inner == null ? null : new PlacementModifier.NotPred(inner);
                }
                case "minecraft:all_of", "minecraft:any_of" -> {
                    ArrayList<PlacementModifier.BlockPred> children = new ArrayList<>();
                    for (JsonElement el : predicate.getAsJsonArray("predicates")) {
                        PlacementModifier.BlockPred child = parseBlockPred(el.getAsJsonObject());
                        if (child == null) return null;
                        children.add(child);
                    }
                    return type.equals("minecraft:all_of")
                        ? new PlacementModifier.AllOfPred(List.copyOf(children))
                        : new PlacementModifier.AnyOfPred(List.copyOf(children));
                }
                default -> {
                    return null;
                }
            }
        }

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

        /** 태그의 모든 블록의 모든 state id(바닐라 BlockState.is(tag)와 같은 의미). */
        private List<Integer> tagStates(String tag) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (String block : BlockTags.get().blocksOf(tag)) ids.addAll(stateIdsOf(block));
            return ids;
        }

        private final Map<String, Set<Integer>> blockStateIdCache = new HashMap<>();

        /** 블록 이름의 모든 state id(캐시). */
        private Set<Integer> stateIdsOf(String block) {
            return blockStateIdCache.computeIfAbsent(block, registry::stateIdsForBlock);
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
                case "minecraft:fixed_placement" -> {
                    java.util.List<BlockPos> ps = new ArrayList<>();
                    for (JsonElement e : json.getAsJsonArray("positions")) {
                        JsonArray a = e.getAsJsonArray();
                        ps.add(new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt()));
                    }
                    out.add(new PlacementModifier.FixedPlacement(List.copyOf(ps)));
                }
                case "minecraft:count_on_every_layer" -> out.add(new PlacementModifier.CountOnEveryLayerPlacement(treeIntProvider(json.get("count"), 1)));
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
                case "minecraft:block_predicate_filter" -> out.add(
                    new PlacementModifier.PredicateFilter(predicates().parse(json.getAsJsonObject("predicate"))));
                case "minecraft:random_offset" -> out.add(new PlacementModifier.RandomOffsetPlacement(
                    treeIntProvider(json.get("xz_spread"), 0), treeIntProvider(json.get("y_spread"), 0)));
                case "minecraft:environment_scan" -> {
                    int[] d = direction(json.get("direction_of_search").getAsString());
                    BlockPredicate allowed = json.has("allowed_search_condition")
                        ? predicates().parse(json.getAsJsonObject("allowed_search_condition")) : new BlockPredicate.True();
                    out.add(new PlacementModifier.EnvironmentScanPlacement(d[0], d[1], d[2],
                        predicates().parse(json.getAsJsonObject("target_condition")), allowed,
                        json.get("max_steps").getAsInt()));
                }
                case "minecraft:carving_mask" -> out.add(new PlacementModifier.CarvingMaskPlacement(
                    json.has("step") ? json.get("step").getAsString() : "air"));
                case "minecraft:surface_relative_threshold_filter" -> {
                    String hmStr = json.has("heightmap") ? json.get("heightmap").getAsString() : "WORLD_SURFACE_WG";
                    int minRel = json.has("min_inclusive") ? json.get("min_inclusive").getAsInt() : Integer.MIN_VALUE;
                    int maxRel = json.has("max_inclusive") ? json.get("max_inclusive").getAsInt() : Integer.MAX_VALUE;
                    out.add(new PlacementModifier.SurfaceRelativeThresholdFilter(parseHeightmapType(json), minRel, maxRel));
                }
                default -> throw new IllegalArgumentException("지원하지 않는 placement modifier: " + type);
            }
        }

        private static int[] direction(String name) {
            return switch (name) {
                case "down" -> new int[]{0, -1, 0};
                case "up" -> new int[]{0, 1, 0};
                case "north" -> new int[]{0, 0, -1};
                case "south" -> new int[]{0, 0, 1};
                case "west" -> new int[]{-1, 0, 0};
                case "east" -> new int[]{1, 0, 0};
                default -> throw new IllegalArgumentException("방향: " + name);
            };
        }

        private BlockPredicate.Parser predicateParser;

        private BlockPredicate.Parser predicates() {
            if (predicateParser == null) {
                FluidKinds fluids = new FluidKinds(registry);
                predicateParser = new BlockPredicate.Parser(registry, tag -> Set.copyOf(tagStates(tag)),
                    new BlockSurvival(registry, fluids), fluids);
            }
            return predicateParser;
        }

        private PlacementModifier countPlacement(JsonElement el) {
            if (el.isJsonPrimitive()) return new PlacementModifier.CountPlacement(el.getAsInt());
            return new PlacementModifier.ProviderCountPlacement(treeIntProvider(el, 1));
        }

        private PlacementModifier.HeightProvider heightProvider(JsonObject json) {
            String type = json.get("type").getAsString();
            int min = PlacementModifier.worldgenYOrDefault(json.get("min_inclusive"), 0, genMinY, genDepth);
            int max = PlacementModifier.worldgenYOrDefault(json.get("max_inclusive"), 0, genMinY, genDepth);
            return switch (type) {
                case "minecraft:uniform" -> new PlacementModifier.UniformHeight(min, max);
                case "minecraft:biased_to_bottom" -> new PlacementModifier.BiasedToBottomHeight(min, max,
                    json.has("inner") ? json.get("inner").getAsInt() : 1);
                case "minecraft:very_biased_to_bottom" -> new PlacementModifier.VeryBiasedToBottomHeight(min, max,
                    json.has("inner") ? json.get("inner").getAsInt() : 1);
                case "minecraft:trapezoid" -> new PlacementModifier.TrapezoidHeight(min, max,
                    json.has("plateau") ? json.get("plateau").getAsInt() : 0);
                case "minecraft:constant" -> new PlacementModifier.ConstantHeight(
                    PlacementModifier.worldgenYOrDefault(json.get("value"), min, genMinY, genDepth));
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
