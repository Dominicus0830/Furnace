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
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.gen.WorldgenData;

public final class BiomeFeatureData {

    public static final int STEP_COUNT = 11;

    private final Map<String, List<List<PlacedFeature>>> biomeFeatures; // 삽입(=load) 순서 보존
    private final Map<PlacedFeature, String> idByFeature; // identity → placed feature id (parity 검증용)

    public BiomeFeatureData(Map<String, List<List<PlacedFeature>>> biomeFeatures) {
        this(biomeFeatures, new IdentityHashMap<>());
    }

    public BiomeFeatureData(Map<String, List<List<PlacedFeature>>> biomeFeatures,
                            Map<PlacedFeature, String> idByFeature) {
        this.biomeFeatures = Collections.unmodifiableMap(new LinkedHashMap<>(biomeFeatures));
        this.idByFeature = Collections.unmodifiableMap(new IdentityHashMap<>(idByFeature));
    }

    /** PlacedFeature identity → 원본 id. 미구현(placeholder) 포함. */
    public String idOf(PlacedFeature feature) {
        return idByFeature.get(feature);
    }

    public static BiomeFeatureData load(WorldgenData data, Iterable<String> biomeIds, BlockRegistry registry) {
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
        return new BiomeFeatureData(loaded, idByFeature);
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
            return placedCache.computeIfAbsent(id, key -> {
                JsonObject json = data.placedFeature(key);
                ConfiguredFeature feature = configuredFeature(featureId(json.get("feature")));
                if (feature == null) return null;
                ArrayList<PlacementModifier> placement = new ArrayList<>();
                JsonArray arr = json.getAsJsonArray("placement");
                if (arr != null) {
                    for (JsonElement el : arr) parsePlacement(el.getAsJsonObject(), placement);
                }
                return new PlacedFeature(feature, List.copyOf(placement));
            });
        }

        private static String featureId(JsonElement el) {
            if (el.isJsonPrimitive()) return el.getAsString();
            JsonObject obj = el.getAsJsonObject();
            return obj.has("name") ? obj.get("name").getAsString() : obj.get("id").getAsString();
        }

        ConfiguredFeature configuredFeature(String id) {
            return configuredCache.computeIfAbsent(id, key -> parseConfigured(data.configuredFeature(key)));
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
            if (type.equals("minecraft:random_patch")) {
                return parseRandomPatch(config);
            }
            if (type.equals("minecraft:simple_block")) {
                return parseSimpleBlock(config);
            }
            if (type.equals("minecraft:freeze_top_layer")) {
                return new FreezeTopLayerFeature(
                    registry.stateIdOrAir("minecraft:water"),
                    registry.stateIdOrAir("minecraft:ice"),
                    registry.stateIdOrAir("minecraft:snow"),
                    biomeTemps);
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

        private ConfiguredFeature parseSimpleBlock(JsonObject config) {
            JsonObject toPlace = config.getAsJsonObject("to_place");
            // 상수 provider만 지원. weighted/noise provider(random 소비)는 추후 슬라이스 → placeholder.
            if (toPlace.has("type") && !toPlace.get("type").getAsString().equals("minecraft:simple_state_provider")) {
                return NoopFeature.INSTANCE;
            }
            return new SimpleBlockFeature(parseStateProvider(toPlace), plantGround());
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

        private DiskFeature parseDisk(JsonObject config) {
            return new DiskFeature(
                parseStateProvider(config.getAsJsonObject("state_provider")),
                intProviderValue(config.get("radius"), 2),
                intProviderValue(config.get("half_height"), 1),
                parseTargetBlocks(config.getAsJsonObject("target")));
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

        private int parseState(JsonObject state) {
            return registry.stateIdOrAir(state.get("Name").getAsString());
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

        // matching_blocks predicate만 지원. 그외 타입은 null → modifier 생략(통과). random 미소비라 스트림 영향 없음.
        private Set<Integer> blockPredicateStates(JsonObject predicate) {
            if (predicate == null) return null;
            String type = predicate.get("type").getAsString();
            if (type.equals("minecraft:matching_blocks")) {
                return parseBlockSet(predicate.get("blocks"));
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
                    "minecraft:andesite", "minecraft:tuff");
                case "minecraft:deepslate_ore_replaceables" -> states("minecraft:deepslate");
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
                case "minecraft:count" -> out.add(new PlacementModifier.CountPlacement(countValue(json.get("count"))));
                case "minecraft:rarity_filter" -> out.add(new PlacementModifier.RarityFilterPlacement(json.get("chance").getAsInt()));
                case "minecraft:in_square" -> out.add(new PlacementModifier.InSquarePlacement());
                case "minecraft:height_range" -> out.add(new PlacementModifier.HeightRangePlacement(heightProvider(json.getAsJsonObject("height"))));
                case "minecraft:biome" -> out.add(new PlacementModifier.BiomeFilter());
                case "minecraft:heightmap" -> out.add(new PlacementModifier.HeightmapPlacement());
                case "minecraft:block_predicate_filter" -> {
                    Set<Integer> allowed = blockPredicateStates(json.getAsJsonObject("predicate"));
                    if (allowed != null) out.add(new PlacementModifier.BlockPredicateFilter(allowed));
                }
                default -> {
                    // Unsupported modifiers are intentionally ignored until their feature type is implemented.
                }
            }
        }

        private static int countValue(JsonElement el) {
            if (el.isJsonPrimitive()) return el.getAsInt();
            JsonObject obj = el.getAsJsonObject();
            return obj.has("value") ? obj.get("value").getAsInt() : 1;
        }

        private static PlacementModifier.HeightProvider heightProvider(JsonObject json) {
            String type = json.get("type").getAsString();
            int min = PlacementModifier.absoluteYOrDefault(json.get("min_inclusive"), 0);
            int max = PlacementModifier.absoluteYOrDefault(json.get("max_inclusive"), 0);
            return switch (type) {
                case "minecraft:uniform" -> new PlacementModifier.UniformHeight(min, max);
                case "minecraft:biased_to_bottom" -> new PlacementModifier.BiasedToBottomHeight(min, max,
                    json.has("inner") ? json.get("inner").getAsInt() : 1);
                case "minecraft:very_biased_to_bottom" -> new PlacementModifier.VeryBiasedToBottomHeight(min, max,
                    json.has("inner") ? json.get("inner").getAsInt() : 1);
                case "minecraft:trapezoid" -> new PlacementModifier.TrapezoidHeight(min, max,
                    json.has("plateau") ? json.get("plateau").getAsInt() : 0);
                case "minecraft:constant" -> new PlacementModifier.ConstantHeight(
                    PlacementModifier.absoluteYOrDefault(json.get("value"), min));
                default -> new PlacementModifier.UniformHeight(min, max);
            };
        }
    }
}
