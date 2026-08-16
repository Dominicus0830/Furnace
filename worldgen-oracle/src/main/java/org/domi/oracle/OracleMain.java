// 바닐라 노이즈 지형 생성을 실행해 bin fixture를 덤프하는 오라클 진입점
package org.domi.oracle;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.Beardifier;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;

import java.util.IdentityHashMap;
import java.io.FileOutputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OracleMain {
    private static final OracleRngTrace ORACLE_RNG_TRACE = new OracleRngTrace();

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: OracleMain <seed> <cx:cz|rng:N> [...]");
            System.exit(1);
        }

        SharedConstants.setVersion(DetectedVersion.BUILT_IN);
        Bootstrap.bootStrap();

        long seed = Long.parseLong(args[0]);

        // Output directory: furnace-server/src/test/resources/worldgen/fixtures/
        Path outDir = Paths.get("furnace-server", "src", "test", "resources", "worldgen", "fixtures");
        Files.createDirectories(outDir);

        // Build vanilla registry lookup (includes all worldgen registries)
        HolderLookup.Provider registries = VanillaRegistries.createLookup();

        // Bind block tags to BuiltInRegistries.BLOCK so carver state.is(tagKey) works.
        // VanillaRegistries.createLookup() runs datagen-mode; its RegistryLookup throws
        // UnsupportedOperationException on listTagIds(). Hardcode the tags we need instead.
        {
            java.util.Map<net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block>,
                         java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.block.Block>>> tagMap = new java.util.LinkedHashMap<>();

            java.util.function.BiConsumer<String, String[]> addTag = (tagPath, blockNames) -> {
                net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> tagKey =
                    net.minecraft.tags.TagKey.create(Registries.BLOCK,
                        net.minecraft.resources.ResourceLocation.parse(tagPath));
                java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.block.Block>> holders = new java.util.ArrayList<>();
                for (String name : blockNames) {
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.block.Block> key =
                        net.minecraft.resources.ResourceKey.create(Registries.BLOCK,
                            net.minecraft.resources.ResourceLocation.parse(name));
                    net.minecraft.core.registries.BuiltInRegistries.BLOCK.getHolder(key).ifPresent(holders::add);
                }
                tagMap.put(tagKey, holders);
            };

            addTag.accept("minecraft:base_stone_overworld", new String[]{
                "minecraft:stone","minecraft:granite","minecraft:diorite","minecraft:andesite","minecraft:tuff","minecraft:deepslate"});
            addTag.accept("minecraft:stone_ore_replaceables", new String[]{
                "minecraft:stone","minecraft:granite","minecraft:diorite","minecraft:andesite"});
            addTag.accept("minecraft:deepslate_ore_replaceables", new String[]{
                "minecraft:deepslate","minecraft:tuff"});
            addTag.accept("minecraft:dirt", new String[]{
                "minecraft:dirt","minecraft:grass_block","minecraft:podzol","minecraft:coarse_dirt","minecraft:mycelium",
                "minecraft:rooted_dirt","minecraft:moss_block","minecraft:pale_moss_block","minecraft:mud","minecraft:muddy_mangrove_roots"});
            addTag.accept("minecraft:sand", new String[]{
                "minecraft:sand","minecraft:red_sand","minecraft:suspicious_sand"});
            addTag.accept("minecraft:terracotta", new String[]{
                "minecraft:terracotta","minecraft:white_terracotta","minecraft:orange_terracotta","minecraft:magenta_terracotta",
                "minecraft:light_blue_terracotta","minecraft:yellow_terracotta","minecraft:lime_terracotta","minecraft:pink_terracotta",
                "minecraft:gray_terracotta","minecraft:light_gray_terracotta","minecraft:cyan_terracotta","minecraft:purple_terracotta",
                "minecraft:blue_terracotta","minecraft:brown_terracotta","minecraft:green_terracotta","minecraft:red_terracotta","minecraft:black_terracotta"});
            addTag.accept("minecraft:iron_ores", new String[]{"minecraft:iron_ore","minecraft:deepslate_iron_ore"});
            addTag.accept("minecraft:copper_ores", new String[]{"minecraft:copper_ore","minecraft:deepslate_copper_ore"});
            addTag.accept("minecraft:snow", new String[]{"minecraft:snow","minecraft:snow_block","minecraft:powder_snow"});
            addTag.accept("minecraft:overworld_carver_replaceables", new String[]{
                "minecraft:stone","minecraft:granite","minecraft:diorite","minecraft:andesite","minecraft:tuff","minecraft:deepslate",
                "minecraft:dirt","minecraft:grass_block","minecraft:podzol","minecraft:coarse_dirt","minecraft:mycelium",
                "minecraft:rooted_dirt","minecraft:moss_block","minecraft:pale_moss_block","minecraft:mud","minecraft:muddy_mangrove_roots",
                "minecraft:sand","minecraft:red_sand","minecraft:suspicious_sand",
                "minecraft:terracotta","minecraft:white_terracotta","minecraft:orange_terracotta","minecraft:magenta_terracotta",
                "minecraft:light_blue_terracotta","minecraft:yellow_terracotta","minecraft:lime_terracotta","minecraft:pink_terracotta",
                "minecraft:gray_terracotta","minecraft:light_gray_terracotta","minecraft:cyan_terracotta","minecraft:purple_terracotta",
                "minecraft:blue_terracotta","minecraft:brown_terracotta","minecraft:green_terracotta","minecraft:red_terracotta","minecraft:black_terracotta",
                "minecraft:iron_ore","minecraft:deepslate_iron_ore",
                "minecraft:copper_ore","minecraft:deepslate_copper_ore",
                "minecraft:snow","minecraft:snow_block","minecraft:powder_snow",
                "minecraft:water","minecraft:gravel","minecraft:suspicious_gravel",
                "minecraft:sandstone","minecraft:red_sandstone","minecraft:calcite","minecraft:packed_ice","minecraft:raw_iron_block","minecraft:raw_copper_block"});

            // data/minecraft/tags/block/leaves.json (1.21.1)
            String[] leavesBlocks = {
                "minecraft:jungle_leaves","minecraft:oak_leaves","minecraft:spruce_leaves","minecraft:dark_oak_leaves",
                "minecraft:acacia_leaves","minecraft:birch_leaves","minecraft:azalea_leaves","minecraft:flowering_azalea_leaves",
                "minecraft:mangrove_leaves","minecraft:cherry_leaves"};
            addTag.accept("minecraft:leaves", leavesBlocks);
            // data/minecraft/tags/block/replaceable_by_trees.json (1.21.1) — #minecraft:leaves 확장 포함
            java.util.List<String> replaceableByTrees = new java.util.ArrayList<>(java.util.List.of(leavesBlocks));
            replaceableByTrees.addAll(java.util.List.of(
                "minecraft:short_grass","minecraft:fern","minecraft:dead_bush","minecraft:vine","minecraft:glow_lichen",
                "minecraft:sunflower","minecraft:lilac","minecraft:rose_bush","minecraft:peony","minecraft:tall_grass",
                "minecraft:large_fern","minecraft:hanging_roots","minecraft:pitcher_plant","minecraft:water",
                "minecraft:seagrass","minecraft:tall_seagrass","minecraft:warped_roots","minecraft:nether_sprouts","minecraft:crimson_roots"));
            addTag.accept("minecraft:replaceable_by_trees", replaceableByTrees.toArray(new String[0]));

            ((net.minecraft.core.MappedRegistry<net.minecraft.world.level.block.Block>)
                (Object) net.minecraft.core.registries.BuiltInRegistries.BLOCK).bindTags(tagMap);
        }

        // Fluid tags — data/minecraft/tags/fluid/{water,lava}.json (1.21.1).
        // 미바인딩 시 FluidTags.WATER가 빈 태그 → TallSeagrassBlock.canSurvive 등 fluid 태그 검사가
        // 전부 false → fixture가 바닐라와 달라짐 (tall_seagrass 전멸 버그의 원인).
        {
            java.util.Map<net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid>,
                         java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>>> fluidTagMap = new java.util.LinkedHashMap<>();
            java.util.function.BiConsumer<String, String[]> addFluidTag = (tagPath, fluidNames) -> {
                net.minecraft.tags.TagKey<net.minecraft.world.level.material.Fluid> tagKey =
                    net.minecraft.tags.TagKey.create(Registries.FLUID,
                        net.minecraft.resources.ResourceLocation.parse(tagPath));
                java.util.List<net.minecraft.core.Holder<net.minecraft.world.level.material.Fluid>> holders = new java.util.ArrayList<>();
                for (String name : fluidNames) {
                    net.minecraft.resources.ResourceKey<net.minecraft.world.level.material.Fluid> key =
                        net.minecraft.resources.ResourceKey.create(Registries.FLUID,
                            net.minecraft.resources.ResourceLocation.parse(name));
                    net.minecraft.core.registries.BuiltInRegistries.FLUID.getHolder(key).ifPresent(holders::add);
                }
                fluidTagMap.put(tagKey, holders);
            };
            addFluidTag.accept("minecraft:water", new String[]{"minecraft:water","minecraft:flowing_water"});
            addFluidTag.accept("minecraft:lava", new String[]{"minecraft:lava","minecraft:flowing_lava"});
            ((net.minecraft.core.MappedRegistry<net.minecraft.world.level.material.Fluid>)
                (Object) net.minecraft.core.registries.BuiltInRegistries.FLUID).bindTags(fluidTagMap);
        }

        // Get OVERWORLD NoiseGeneratorSettings and clone with aquifer + oreVeins disabled
        HolderLookup<NoiseGeneratorSettings> noiseSettingsReg = registries.lookupOrThrow(Registries.NOISE_SETTINGS);
        NoiseGeneratorSettings overworldSettings = noiseSettingsReg.getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();

        NoiseGeneratorSettings noAquiferSettings = new NoiseGeneratorSettings(
            overworldSettings.noiseSettings(),
            overworldSettings.defaultBlock(),
            overworldSettings.defaultFluid(),
            overworldSettings.noiseRouter(),
            overworldSettings.surfaceRule(),
            overworldSettings.spawnTarget(),
            overworldSettings.seaLevel(),
            overworldSettings.disableMobGeneration(),
            false, // aquifersEnabled
            false, // oreVeinsEnabled
            overworldSettings.useLegacyRandomSource()
        );

        // Wrap in a Holder (direct holder)
        Holder<NoiseGeneratorSettings> settingsHolder = Holder.direct(noAquiferSettings);

        // Build RandomState: needs the noise parameter getter
        HolderLookup<NormalNoise.NoiseParameters> noiseGetter = registries.lookupOrThrow(Registries.NOISE);
        RandomState randomState = RandomState.create(noAquiferSettings, noiseGetter, seed);

        // Build biome source from the OVERWORLD preset
        HolderLookup<MultiNoiseBiomeSourceParameterList> mnbsplLookup =
            registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST);
        Holder<MultiNoiseBiomeSourceParameterList> overworldPreset =
            mnbsplLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
        MultiNoiseBiomeSource biomeSource = MultiNoiseBiomeSource.createFromPreset(overworldPreset);

        // Aquifer-enabled resources (real overworld settings)
        Holder<NoiseGeneratorSettings> aquiferSettingsHolder = noiseSettingsReg.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        RandomState aquiferRandomState = RandomState.create(aquiferSettingsHolder.value(), noiseGetter, seed);
        NoiseBasedChunkGenerator aquiferGenerator = new NoiseBasedChunkGenerator(biomeSource, aquiferSettingsHolder);
        Aquifer.FluidStatus lavaStatusAq = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus seaStatusAq = new Aquifer.FluidStatus(aquiferSettingsHolder.value().seaLevel(), aquiferSettingsHolder.value().defaultFluid());
        Aquifer.FluidPicker aquiferFluidPicker = (x, y, z) -> y < Math.min(-54, aquiferSettingsHolder.value().seaLevel()) ? lavaStatusAq : seaStatusAq;

        // Build a real Registry<Biome> from the lookup, needed by ProtoChunk constructor.
        // HolderLookup.RegistryLookup is not a Registry, so we populate a MappedRegistry.
        HolderLookup.RegistryLookup<Biome> biomeLookup = registries.lookupOrThrow(Registries.BIOME);
        MappedRegistry<Biome> biomeRegistry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        sortedElements(biomeLookup).forEach(ref ->
            biomeRegistry.register(ref.key(), ref.value(), RegistrationInfo.BUILT_IN)
        );
        Registry<Biome> frozenBiomeRegistry = biomeRegistry.freeze();

        // Build a RegistryAccess from data-driven registries needed by carvers.
        // VanillaRegistries.createLookup() returns HolderLookup.Provider which is NOT a
        // RegistryAccess, so we populate MappedRegistry instances and wrap them.
        HolderLookup.RegistryLookup<ConfiguredWorldCarver<?>> carverLookup =
            registries.lookupOrThrow(Registries.CONFIGURED_CARVER);
        MappedRegistry<ConfiguredWorldCarver<?>> carverRegistry =
            new MappedRegistry<>(Registries.CONFIGURED_CARVER, Lifecycle.stable());
        sortedElements(carverLookup).forEach(ref ->
            carverRegistry.register(ref.key(), ref.value(), RegistrationInfo.BUILT_IN)
        );
        Registry<ConfiguredWorldCarver<?>> frozenCarverRegistry = carverRegistry.freeze();

        RegistryAccess registryAccess = buildRegistryAccess(registries, frozenBiomeRegistry, frozenCarverRegistry);

        if (args.length > 1 && args[1].equals("dumpsurfacerule")) {
            com.mojang.serialization.Codec<net.minecraft.world.level.levelgen.SurfaceRules.RuleSource> codec =
                net.minecraft.world.level.levelgen.SurfaceRules.RuleSource.CODEC;
            com.google.gson.JsonElement json = codec.encodeStart(
                net.minecraft.resources.RegistryOps.create(
                    com.mojang.serialization.JsonOps.INSTANCE, registries),
                overworldSettings.surfaceRule()).getOrThrow();
            System.out.println(new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json));
            return;
        }

        // Process each argument
        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("rng:")) {
                int n = Integer.parseInt(arg.substring(4));
                dumpRng(seed, n, outDir);
            } else if (arg.startsWith("improved:")) {
                long noiseSeed = Long.parseLong(arg.substring("improved:".length()));
                dumpImprovedNoise(noiseSeed, outDir);
            } else if (arg.startsWith("normal:")) {
                long noiseSeed = Long.parseLong(arg.substring("normal:".length()));
                dumpNormalNoise(noiseSeed, outDir);
            } else if (arg.startsWith("blended:")) {
                long noiseSeed = Long.parseLong(arg.substring("blended:".length()));
                dumpBlendedNoise(noiseSeed, outDir);
            } else if (arg.equals("params")) {
                Path mainResDir = Paths.get("furnace-server", "src", "main", "resources", "worldgen", "1.21.1");
                dumpBiomeParams(mainResDir);
            } else if (arg.startsWith("biome:")) {
                String[] parts = arg.substring("biome:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpBiomeChunk(seed, cx, cz, biomeSource, randomState, outDir);
            } else if (arg.startsWith("biomeblock:")) {
                String[] parts = arg.substring("biomeblock:".length()).split(":");
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                dumpBiomeBlock(seed, x, y, z, biomeSource, randomState);
            } else if (arg.startsWith("surface:")) {
                String[] parts = arg.substring("surface:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpSurfaceChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState, aquiferSettingsHolder.value(),
                                 aquiferFluidPicker, frozenBiomeRegistry, biomeSource, outDir);
            } else if (arg.startsWith("aquifer:")) {
                String[] parts = arg.substring("aquifer:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpAquiferChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState,
                                 aquiferSettingsHolder.value(), aquiferFluidPicker,
                                 frozenBiomeRegistry, biomeSource, outDir);
            } else if (arg.startsWith("carver:")) {
                String[] parts = arg.substring("carver:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpCarverChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState,
                                aquiferSettingsHolder.value(), aquiferFluidPicker,
                                frozenBiomeRegistry, biomeSource, registryAccess, outDir);
            } else if (arg.startsWith("featurefull:")) {
                String[] parts = arg.substring("featurefull:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpFeatureFullChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState,
                                    aquiferSettingsHolder.value(), aquiferFluidPicker,
                                    frozenBiomeRegistry, biomeSource, registryAccess, registries, outDir);
            } else if (arg.startsWith("featurespill:")) {
                String[] parts = arg.substring("featurespill:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpFeatureSpillChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState,
                                    aquiferSettingsHolder.value(), aquiferFluidPicker,
                                    frozenBiomeRegistry, biomeSource, registryAccess, registries, outDir);
            } else if (arg.equals("featureorder")) {
                dumpFeatureOrder(registries, biomeSource, outDir);
            } else if (arg.equals("structorder")) {
                dumpStructureOrder(registryAccess, outDir);
            } else if (arg.equals("xortest")) {
                dumpXoroshiroCheck();
            } else if (arg.equals("blocks")) {
                Path blocksResDir = Paths.get("furnace-server", "src", "main", "resources", "blocks");
                dumpBlocks(blocksResDir);
            } else if (arg.equals("biomeinfonoise")) {
                dumpBiomeInfoNoise(outDir);
            } else if (arg.equals("allstates")) {
                Path blocksResDir = Paths.get("furnace-server", "src", "main", "resources", "blocks");
                dumpAllStates(blocksResDir);
            } else if (arg.startsWith("legacynormal:")) {
                long noiseSeed = Long.parseLong(arg.substring("legacynormal:".length()));
                dumpLegacyNormalNoise(noiseSeed, outDir);
            } else if (arg.startsWith("featurestruct:")) {
                String[] parts = arg.substring("featurestruct:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpFeatureStructChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState,
                                    aquiferSettingsHolder.value(), aquiferFluidPicker,
                                    frozenBiomeRegistry, biomeSource, registryAccess, registries, outDir);
            } else if (arg.startsWith("structplacement:")) {
                String[] parts = arg.substring("structplacement:".length()).split(":");
                int cx1 = Integer.parseInt(parts[0]);
                int cz1 = Integer.parseInt(parts[1]);
                int cx2 = Integer.parseInt(parts[2]);
                int cz2 = Integer.parseInt(parts[3]);
                dumpStructPlacement(seed, cx1, cz1, cx2, cz2, outDir);
            } else {
                String[] parts = arg.split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState, aquiferSettingsHolder.value(), aquiferFluidPicker, frozenBiomeRegistry, outDir);
            }
        }

        System.out.println("Oracle done. Output: " + outDir.toAbsolutePath());
    }

    private static RegistryAccess buildRegistryAccess(
        HolderLookup.Provider registries,
        Registry<Biome> frozenBiomeRegistry,
        Registry<ConfiguredWorldCarver<?>> frozenCarverRegistry
    ) {
        java.util.List<Registry<?>> copied = new java.util.ArrayList<>();
        registries.listRegistries().forEach(key -> {
            if (key.equals(Registries.BIOME)) {
                copied.add(frozenBiomeRegistry);
            } else if (key.equals(Registries.CONFIGURED_CARVER)) {
                copied.add(frozenCarverRegistry);
            } else {
                copied.add(copyRegistry(registries, (ResourceKey) key));
            }
        });
        return new RegistryAccess.ImmutableRegistryAccess(copied);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Registry<?> copyRegistry(HolderLookup.Provider registries, ResourceKey key) {
        HolderLookup.RegistryLookup lookup = registries.lookupOrThrow(key);
        MappedRegistry registry = new MappedRegistry(key, Lifecycle.stable());
        java.util.List<Holder.Reference> refs = new java.util.ArrayList<>();
        lookup.listElements().forEach(refObj -> refs.add((Holder.Reference) refObj));
        refs.sort(java.util.Comparator.comparing(ref -> ref.key().location().toString()));
        refs.forEach(ref -> registry.register(ref.key(), ref.value(), RegistrationInfo.BUILT_IN));
        return registry.freeze();
    }

    /**
     * VanillaRegistries.createLookup()의 HolderLookup.RegistryLookup.listElements() 순서는 JVM 실행마다
     * 요동한다 (실측: d3, structure_order.txt 5회 중 4회 diff). 등록 순서를 재현할 신뢰 가능한 실서버 근거가
     * 없으므로(.mca 게이트 이연) id 사전순으로 결정적 핀 고정 — 실서버(RegistryDataLoader) 순서와의 일치는
     * 미보장이나, 최소한 oracle 재실행 간 fixture가 결정적이 되도록 한다.
     */
    private static <T> java.util.List<Holder.Reference<T>> sortedElements(HolderLookup.RegistryLookup<T> lookup) {
        java.util.List<Holder.Reference<T>> refs = new java.util.ArrayList<>();
        lookup.listElements().forEach(refs::add);
        refs.sort(java.util.Comparator.comparing(ref -> ref.key().location().toString()));
        return refs;
    }

    /**
     * Dumps 50 NormalNoise samples built from vanilla XoroshiroRandomSource(noiseSeed).
     * Parameters: firstOctave=-7, amplitudes=[1.0, 1.0, 1.0, 1.0] (4 octaves).
     * Coordinate set (i=0..49): x = i*12.3 - 100, y = i*4.7 + 3, z = -i*8.1 + 50.
     * Format: little-endian int32 firstOctave; int32 ampCount; ampCount×double amplitudes;
     *         int32 N; then N records of (double x, double y, double z, double value).
     */
    private static void dumpNormalNoise(long noiseSeed, Path outDir) throws Exception {
        int firstOctave = -7;
        double[] amps = {1.0, 1.0, 1.0, 1.0};
        NormalNoise.NoiseParameters params = new NormalNoise.NoiseParameters(firstOctave, new DoubleArrayList(amps));
        NormalNoise noise = NormalNoise.create(new XoroshiroRandomSource(noiseSeed), params);
        int n = 50;
        // header: int32 firstOctave + int32 ampCount + ampCount*double + int32 N
        // data:   N * 4 * double
        int headerBytes = 4 + 4 + amps.length * 8 + 4;
        int dataBytes = n * 4 * 8;
        ByteBuffer buf = ByteBuffer.allocate(headerBytes + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(firstOctave);
        buf.putInt(amps.length);
        for (double a : amps) buf.putDouble(a);
        buf.putInt(n);
        for (int i = 0; i < n; i++) {
            double x = i * 12.3 - 100.0;
            double y = i * 4.7 + 3.0;
            double z = -i * 8.1 + 50.0;
            double value = noise.getValue(x, y, z);
            buf.putDouble(x);
            buf.putDouble(y);
            buf.putDouble(z);
            buf.putDouble(value);
        }
        Path out = outDir.resolve("noise_normal_" + noiseSeed + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.println("Wrote " + out + " (" + buf.capacity() + " bytes, n=" + n + ")");
    }

    /**
     * Dumps 50 ImprovedNoise samples built from vanilla XoroshiroRandomSource(noiseSeed).
     * Coordinate set (i=0..49): x = i*12.3 - 100, y = i*4.7 + 3, z = -i*8.1 + 50.
     * Format: little-endian int32 N; then N records of (double x, double y, double z, double value).
     */
    private static void dumpImprovedNoise(long noiseSeed, Path outDir) throws Exception {
        ImprovedNoise noise = new ImprovedNoise(new XoroshiroRandomSource(noiseSeed));
        int n = 50;
        // 4 bytes N + 50 * (3 coords + 1 value) * 8 bytes each = 4 + 50*32 = 1604 bytes
        ByteBuffer buf = ByteBuffer.allocate(4 + n * 4 * 8).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(n);
        for (int i = 0; i < n; i++) {
            double x = i * 12.3 - 100.0;
            double y = i * 4.7 + 3.0;
            double z = -i * 8.1 + 50.0;
            double value = noise.noise(x, y, z);
            buf.putDouble(x);
            buf.putDouble(y);
            buf.putDouble(z);
            buf.putDouble(value);
        }
        Path out = outDir.resolve("noise_improved_" + noiseSeed + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.println("Wrote " + out + " (" + buf.capacity() + " bytes, n=" + n + ")");
    }

    /**
     * Dumps 50 BlendedNoise samples using overworld params (xzScale=0.25, yScale=0.125,
     * xzFactor=80.0, yFactor=160.0, smearScaleMultiplier=8.0) seeded with XoroshiroRandomSource(noiseSeed).
     * Integer block coords (i=0..49): x = i*7-150, y = i*3-30, z = i*-5+64.
     * Format: little-endian 5×double params + int32 N + N×(int32 x, int32 y, int32 z, double value).
     */
    private static void dumpBlendedNoise(long noiseSeed, Path outDir) throws Exception {
        // Overworld old_blended_noise params (from density_function/overworld/base_3d_noise.json)
        double xzScale = 0.25;
        double yScale  = 0.125;
        double xzFactor = 80.0;
        double yFactor  = 160.0;
        double smear    = 8.0;

        BlendedNoise noise = BlendedNoise.createUnseeded(xzScale, yScale, xzFactor, yFactor, smear)
            .withNewRandom(new XoroshiroRandomSource(noiseSeed));

        int n = 50;
        // 5 doubles (params) + int32 N + N * (3 int32 + 1 double)
        int headerBytes = 5 * 8 + 4;
        int dataBytes   = n * (3 * 4 + 8);
        ByteBuffer buf = ByteBuffer.allocate(headerBytes + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        buf.putDouble(xzScale);
        buf.putDouble(yScale);
        buf.putDouble(xzFactor);
        buf.putDouble(yFactor);
        buf.putDouble(smear);
        buf.putInt(n);
        for (int i = 0; i < n; i++) {
            int x = i * 7 - 150;
            int y = i * 3 - 30;
            int z = i * -5 + 64;
            double value = noise.compute(new DensityFunction.SinglePointContext(x, y, z));
            buf.putInt(x);
            buf.putInt(y);
            buf.putInt(z);
            buf.putDouble(value);
        }
        Path out = outDir.resolve("noise_blended_" + noiseSeed + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.println("Wrote " + out + " (" + buf.capacity() + " bytes, n=" + n + ")");
    }

    /**
     * vanilla possibleBiomes() 순서 + vanilla FeatureSorter의 step별 정렬된 placed feature id를 덤프.
     * Furnace FeatureSorter parity 검증용 (광석 좌표 결정자 = step별 index).
     */
    private static void dumpFeatureOrder(
        net.minecraft.core.HolderLookup.Provider registries,
        MultiNoiseBiomeSource biomeSource,
        Path outDir
    ) throws Exception {
        net.minecraft.core.HolderLookup.RegistryLookup<PlacedFeature> pfLookup =
            registries.lookupOrThrow(Registries.PLACED_FEATURE);
        IdentityHashMap<PlacedFeature, String> idByValue = new IdentityHashMap<>();
        pfLookup.listElements().forEach(ref -> idByValue.put(ref.value(), ref.key().location().toString()));

        java.util.List<Holder<Biome>> biomes = java.util.List.copyOf(biomeSource.possibleBiomes());
        java.util.List<String> biomeIds = new java.util.ArrayList<>();
        for (Holder<Biome> b : biomes) biomeIds.add(b.unwrapKey().orElseThrow().location().toString());

        java.util.List<FeatureSorter.StepFeatureData> steps = FeatureSorter.buildFeaturesPerStep(
            biomes, b -> b.value().getGenerationSettings().features(), true);

        StringBuilder sb = new StringBuilder();
        sb.append("BIOMES ").append(biomeIds.size()).append("\n");
        for (String id : biomeIds) sb.append(id).append("\n");
        for (int s = 0; s < steps.size(); s++) {
            java.util.List<PlacedFeature> feats = steps.get(s).features();
            sb.append("STEP ").append(s).append(" ").append(feats.size()).append("\n");
            for (PlacedFeature pf : feats) {
                String id = idByValue.get(pf);
                sb.append(id == null ? "?UNKNOWN" : id).append("\n");
            }
        }
        Path out = outDir.resolve("feature_order.txt");
        Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out + " biomes=" + biomeIds.size() + " steps=" + steps.size());
    }

    /**
     * vanilla ChunkGenerator.applyBiomeDecoration의 registry.stream().collect(groupingBy(step ordinal))
     * 순서를 그대로 재현해 덤프 (d3 — registry 순서 JVM-랜덤 여부 실측용). FeaturePlacer.DESERT_PYRAMID_STRUCTURE_INDEX(=22)의
     * 근거인 STRUCTURE_STEP(=4) 그룹 내 desert_pyramid 순번을 재현한다.
     * Format: 텍스트. 각 줄 "STEP <step> <count>", 그 아래 그룹 내 순서대로 structure id.
     */
    private static void dumpStructureOrder(RegistryAccess registryAccess, Path outDir) throws Exception {
        Registry<net.minecraft.world.level.levelgen.structure.Structure> registry =
            registryAccess.registryOrThrow(Registries.STRUCTURE);
        java.util.Map<Integer, java.util.List<net.minecraft.world.level.levelgen.structure.Structure>> map =
            registry.stream().collect(java.util.stream.Collectors.groupingBy(
                s -> s.step().ordinal()));

        StringBuilder sb = new StringBuilder();
        for (int step : new java.util.TreeSet<>(map.keySet())) {
            java.util.List<net.minecraft.world.level.levelgen.structure.Structure> group = map.get(step);
            sb.append("STEP ").append(step).append(" ").append(group.size()).append("\n");
            for (net.minecraft.world.level.levelgen.structure.Structure s : group) {
                sb.append(registry.getResourceKey(s).map(Object::toString).orElse("?UNKNOWN")).append("\n");
            }
        }
        Path out = outDir.resolve("structure_order.txt");
        Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out);
    }

    /**
     * vanilla RandomSpreadStructurePlacement (desert_pyramids, shipwrecks) placement 판정 덤프.
     * 두 StructureSet 모두 frequency=1.0/exclusion_zone 없음(구조물 registry 실측, §Issues 가정 A) →
     * isStructureChunk == isPlacementChunk(candidate==self)만으로 충분, ChunkGeneratorStructureState 불요.
     * Format: 텍스트. "CHUNK cx cz N" 다음 N줄 setId (cx1..cx2, cz1..cz2 순회, set 순서 desert_pyramids,shipwrecks 고정).
     */
    private static void dumpStructPlacement(long seed, int cx1, int cz1, int cx2, int cz2, Path outDir) throws Exception {
        // 실측: data/minecraft/worldgen/structure_set/{desert_pyramids,shipwrecks}.json
        RandomSpreadStructurePlacement desertPyramids =
            new RandomSpreadStructurePlacement(32, 8, RandomSpreadType.LINEAR, 14357617);
        RandomSpreadStructurePlacement shipwrecks =
            new RandomSpreadStructurePlacement(24, 4, RandomSpreadType.LINEAR, 165745295);

        StringBuilder sb = new StringBuilder();
        int chunkCount = 0;
        for (int cx = cx1; cx <= cx2; cx++) {
            for (int cz = cz1; cz <= cz2; cz++) {
                java.util.List<String> matches = new java.util.ArrayList<>();
                ChunkPos dp = desertPyramids.getPotentialStructureChunk(seed, cx, cz);
                if (dp.x == cx && dp.z == cz) matches.add("minecraft:desert_pyramids");
                ChunkPos sw = shipwrecks.getPotentialStructureChunk(seed, cx, cz);
                if (sw.x == cx && sw.z == cz) matches.add("minecraft:shipwrecks");
                sb.append("CHUNK ").append(cx).append(" ").append(cz).append(" ").append(matches.size()).append("\n");
                for (String id : matches) sb.append(id).append("\n");
                chunkCount++;
            }
        }
        Path out = outDir.resolve("structplacement_" + seed + "_" + cx1 + "_" + cz1 + "_" + cx2 + "_" + cz2 + ".txt");
        Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out + " chunks=" + chunkCount);
    }

    /**
     * vanilla Biome.BIOME_INFO_NOISE (PerlinSimplexNoise, LegacyRandomSource(2345), octaves=[0]) 값 덤프.
     * noise_threshold_count/noise_based_count parity 검증용. 실사용대로 getValue(bx/200, bz/200, false).
     * Format: little-endian int32 N; N×(int32 bx, int32 bz, double value).
     */
    private static void dumpBiomeInfoNoise(Path outDir) throws Exception {
        int n = 40;
        ByteBuffer buf = ByteBuffer.allocate(4 + n * (4 + 4 + 8)).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(n);
        for (int i = 0; i < n; i++) {
            int bx = i * 37 - 300;
            int bz = -i * 53 + 250;
            double value = Biome.BIOME_INFO_NOISE.getValue(bx / 200.0, bz / 200.0, false);
            buf.putInt(bx);
            buf.putInt(bz);
            buf.putDouble(value);
        }
        Path out = outDir.resolve("biome_info_noise.bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.println("Wrote " + out + " (" + buf.capacity() + " bytes, n=" + n + ")");
    }

    /**
     * vanilla NormalNoise (LegacyRandomSource 경로 = state provider noise) 값 덤프.
     * params: firstOctave=0, amplitudes=[1.0] (flower noise_threshold_provider 설정).
     * 좌표: flower 실사용대로 block*scale(0.005). i=0..39.
     * Format: int32 firstOctave; int32 ampCount; ampCount×double amps; int64 seed; int32 N;
     *         N×(double x, double y, double z, double value). LITTLE_ENDIAN.
     */
    private static void dumpLegacyNormalNoise(long noiseSeed, Path outDir) throws Exception {
        int firstOctave = 0;
        double[] amps = {1.0};
        double scale = 0.005;
        NormalNoise.NoiseParameters params =
            new NormalNoise.NoiseParameters(firstOctave, new DoubleArrayList(amps));
        NormalNoise noise = NormalNoise.create(
            new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(noiseSeed)),
            params);
        int n = 40;
        int headerBytes = 4 + 4 + amps.length * 8 + 8 + 4;
        ByteBuffer buf = ByteBuffer.allocate(headerBytes + n * 4 * 8).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(firstOctave);
        buf.putInt(amps.length);
        for (double a : amps) buf.putDouble(a);
        buf.putLong(noiseSeed);
        buf.putInt(n);
        for (int i = 0; i < n; i++) {
            double x = (i * 37 - 300) * scale;
            double y = (i * 11 + 60) * scale;
            double z = (-i * 53 + 250) * scale;
            double value = noise.getValue(x, y, z);
            buf.putDouble(x);
            buf.putDouble(y);
            buf.putDouble(z);
            buf.putDouble(value);
        }
        Path out = outDir.resolve("legacy_normal_" + noiseSeed + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.println("Wrote " + out + " (" + buf.capacity() + " bytes, n=" + n + ")");
    }

    /**
     * 모든 블록의 모든 possibleState → exact global state id 덤프.
     * canonical key (Furnace BlockStateKey.of와 byte-match):
     *   props 없음: "name" / props 있음: "name;k1=v1,k2=v2" (property 이름 오름차순, value=Property.getName(value)).
     * Format: "# states <total>" + 상태별 "<canonicalKey> <id>". → blocks/blockstates_1.21.1.txt
     */
    private static void dumpAllStates(Path outDir) throws Exception {
        Files.createDirectories(outDir);
        int totalStates = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size();
        StringBuilder sb = new StringBuilder();
        sb.append("# states ").append(totalStates).append("\n");
        for (net.minecraft.world.level.block.Block block :
                net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
            for (net.minecraft.world.level.block.state.BlockState state :
                    block.getStateDefinition().getPossibleStates()) {
                sb.append(canonicalKey(name, state)).append(' ')
                  .append(net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state)).append('\n');
            }
        }
        Path out = outDir.resolve("blockstates_1.21.1.txt");
        Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out + " (" + totalStates + " total states)");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String canonicalKey(String name, net.minecraft.world.level.block.state.BlockState state) {
        java.util.Map<net.minecraft.world.level.block.state.properties.Property<?>, Comparable<?>>
            values = state.getValues();
        if (values.isEmpty()) return name;
        java.util.TreeMap<String, String> sorted = new java.util.TreeMap<>();
        for (var e : values.entrySet()) {
            net.minecraft.world.level.block.state.properties.Property prop = e.getKey();
            sorted.put(prop.getName(), prop.getName((Comparable) e.getValue()));
        }
        StringBuilder sb = new StringBuilder(name).append(';');
        boolean first = true;
        for (var e : sorted.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.toString();
    }

    private static void dumpRng(long seed, int n, Path outDir) throws Exception {
        XoroshiroRandomSource rng = new XoroshiroRandomSource(seed);
        ByteBuffer buf = ByteBuffer.allocate(4 + n * 8).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(n);
        for (int i = 0; i < n; i++) {
            buf.putLong(rng.nextLong());
        }
        Path out = outDir.resolve("rng_" + seed + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.println("Wrote " + out + " (" + buf.capacity() + " bytes)");
    }

    private static void dumpBiomeParams(Path outDir) throws Exception {
        Climate.ParameterList<ResourceKey<Biome>> paramList =
            MultiNoiseBiomeSourceParameterList.knownPresets()
                .get(MultiNoiseBiomeSourceParameterList.Preset.OVERWORLD);

        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (com.mojang.datafixers.util.Pair<Climate.ParameterPoint, ResourceKey<Biome>> pair
                : paramList.values()) {
            Climate.ParameterPoint pp = pair.getFirst();
            String biomeName = pair.getSecond().location().toString();
            if (!first) sb.append(",");
            sb.append("\n  {")
              .append("\"biome\":\"").append(biomeName).append("\"")
              .append(",\"temperature\":[").append(pp.temperature().min()).append(",").append(pp.temperature().max()).append("]")
              .append(",\"humidity\":[").append(pp.humidity().min()).append(",").append(pp.humidity().max()).append("]")
              .append(",\"continentalness\":[").append(pp.continentalness().min()).append(",").append(pp.continentalness().max()).append("]")
              .append(",\"erosion\":[").append(pp.erosion().min()).append(",").append(pp.erosion().max()).append("]")
              .append(",\"depth\":[").append(pp.depth().min()).append(",").append(pp.depth().max()).append("]")
              .append(",\"weirdness\":[").append(pp.weirdness().min()).append(",").append(pp.weirdness().max()).append("]")
              .append(",\"offset\":").append(pp.offset())
              .append("}");
            first = false;
        }
        sb.append("\n]\n");

        java.nio.file.Files.createDirectories(outDir);
        Path out = outDir.resolve("biome_parameters.json");
        java.nio.file.Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out + " (" + paramList.values().size() + " entries)");
    }

    private static void dumpChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        Path outDir
    ) throws Exception {
        ChunkPos chunkPos = new ChunkPos(cx, cz);

        // LevelHeightAccessor for overworld: min_y=-64, height=384
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(FixtureFormat.MIN_Y, FixtureFormat.HEIGHT);

        // Build ProtoChunk with no blending data and no upgrade data
        ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, heightAccessor, biomeRegistry, null);

        // Pre-seed the noise chunk cache with an empty Beardifier (no structures).
        // This avoids needing a real StructureManager: the cache is checked first in
        // getOrCreateNoiseChunk(), so fillFromNoise will reuse this and never call
        // Beardifier.forStructuresInChunk().
        Beardifier emptyBeardifier = new Beardifier(
            new ObjectArrayList<Beardifier.Rigid>().iterator(),
            new ObjectArrayList<JigsawJunction>().iterator()
        );
        chunk.getOrCreateNoiseChunk(ca ->
            NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty())
        );

        // Run fillFromNoise — passes null for StructureManager since the noise chunk is
        // already cached and createNoiseChunk (which uses StructureManager) won't be called.
        generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();

        // Collect categories
        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        byte[] categories = new byte[totalBlocks];

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    categories[FixtureFormat.blockIndex(localX, y, localZ)] = classify(state);
                }
            }
        }

        // Write fixture
        int headerSize = 4 + 8 + 4 + 4; // magic + seed + cx + cz
        ByteBuffer buf = ByteBuffer.allocate(headerSize + totalBlocks).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x46584331); // "FXC1"
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);
        buf.put(categories);

        Path out = outDir.resolve("chunk_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }

        // Count categories for sanity report
        int[] counts = new int[5];
        for (byte b : categories) counts[b & 0xFF]++;
        System.out.printf("Wrote %s (%d bytes) air=%d stone=%d water=%d lava=%d other=%d%n",
            out, buf.capacity(), counts[0], counts[1], counts[2], counts[3], counts[4]);

        if (counts[4] > 0) {
            System.err.println("WARNING: " + counts[4] + " OTHER blocks in chunk " + cx + "," + cz);
        }
    }

    private static void dumpSurfaceChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        Path outDir
    ) throws Exception {
        ChunkPos chunkPos = new ChunkPos(cx, cz);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(FixtureFormat.MIN_Y, FixtureFormat.HEIGHT);
        ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, heightAccessor, biomeRegistry, null);

        // Pre-seed noise chunk cache (avoids StructureManager NPE in fillFromNoise)
        Beardifier emptyBeardifier = new Beardifier(
            new ObjectArrayList<Beardifier.Rigid>().iterator(),
            new ObjectArrayList<JigsawJunction>().iterator()
        );
        chunk.getOrCreateNoiseChunk(ca ->
            NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty())
        );

        // 1. Noise fill
        generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();

        // 2. Surface rules — adapt biomeSource to BiomeManager.NoiseBiomeSource via lambda
        BiomeManager biomeManager = new BiomeManager(
            (x, y, z) -> biomeSource.getNoiseBiome(x, y, z, randomState.sampler()),
            BiomeManager.obfuscateSeed(seed)
        );
        WorldGenerationContext worldGenContext = new WorldGenerationContext(generator, heightAccessor);
        generator.buildSurface(chunk, worldGenContext, randomState, null, biomeManager, biomeRegistry, Blender.empty());

        // 3. Dump full block state IDs as shorts
        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        int headerSize  = 4 + 8 + 4 + 4; // magic + seed + cx + cz
        ByteBuffer buf  = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(FixtureFormat.SURFACE_MAGIC);
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    short id = (short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state);
                    buf.putShort(id);
                }
            }
        }

        Path out = outDir.resolve("surface_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
    }

    private static void dumpAquiferChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        Path outDir
    ) throws Exception {
        ChunkPos chunkPos = new ChunkPos(cx, cz);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(FixtureFormat.MIN_Y, FixtureFormat.HEIGHT);
        ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, heightAccessor, biomeRegistry, null);

        Beardifier emptyBeardifier = new Beardifier(
            new ObjectArrayList<Beardifier.Rigid>().iterator(),
            new ObjectArrayList<JigsawJunction>().iterator()
        );
        chunk.getOrCreateNoiseChunk(ca ->
            NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty())
        );

        generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();

        BiomeManager biomeManager = new BiomeManager(
            (x, y, z) -> biomeSource.getNoiseBiome(x, y, z, randomState.sampler()),
            BiomeManager.obfuscateSeed(seed)
        );
        WorldGenerationContext worldGenContext = new WorldGenerationContext(generator, heightAccessor);
        generator.buildSurface(chunk, worldGenContext, randomState, null, biomeManager, biomeRegistry, Blender.empty());

        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        int headerSize  = 4 + 8 + 4 + 4;
        ByteBuffer buf  = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x41515546);
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    short id = (short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state);
                    buf.putShort(id);
                }
            }
        }

        Path out = outDir.resolve("aquifer_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
    }

    private static void dumpCarverChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        net.minecraft.core.RegistryAccess registryAccess,
        Path outDir
    ) throws Exception {
        ChunkPos chunkPos = new ChunkPos(cx, cz);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(FixtureFormat.MIN_Y, FixtureFormat.HEIGHT);
        ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, heightAccessor, biomeRegistry, null);

        Beardifier emptyBeardifier = new Beardifier(
            new ObjectArrayList<Beardifier.Rigid>().iterator(),
            new ObjectArrayList<JigsawJunction>().iterator()
        );
        chunk.getOrCreateNoiseChunk(ca ->
            NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty())
        );

        // 1. Fill terrain
        generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();

        // 2. Surface rules
        BiomeManager biomeManager = new BiomeManager(
            (x, y, z) -> biomeSource.getNoiseBiome(x, y, z, randomState.sampler()),
            BiomeManager.obfuscateSeed(seed)
        );
        WorldGenerationContext worldGenContext = new WorldGenerationContext(generator, heightAccessor);
        generator.buildSurface(chunk, worldGenContext, randomState, null, biomeManager, biomeRegistry, Blender.empty());

        // 3. Get noise chunk and aquifer
        net.minecraft.world.level.levelgen.NoiseChunk noiseChunk =
            chunk.getOrCreateNoiseChunk(ca ->
                NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty()));
        net.minecraft.world.level.levelgen.Aquifer aquifer = noiseChunk.aquifer();
        // DEBUG: check aquifer at (3,-47,0) with density=0.0 (carver call)
        if (cx == 0 && cz == 0) {
            net.minecraft.world.level.block.state.BlockState aq0 = aquifer.computeSubstance(
                new DensityFunction.SinglePointContext(3, -47, 0), 0.0);
            System.out.printf("DBG_AQ aquifer(3,-47,0) density=0.0 → %s%n", aq0);
        }

        // 4. Build CarvingContext
        net.minecraft.world.level.levelgen.carver.CarvingContext carvingCtx =
            new net.minecraft.world.level.levelgen.carver.CarvingContext(
                generator, registryAccess, heightAccessor,
                noiseChunk, randomState, settings.surfaceRule(), null);

        // 5. Hard-code 3 overworld configured carvers
        var carverReg = registryAccess.lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_CARVER);
        var caveCarver = carverReg.getOrThrow(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.CONFIGURED_CARVER,
            net.minecraft.resources.ResourceLocation.parse("minecraft:cave")));
        var caveExtraCarver = carverReg.getOrThrow(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.CONFIGURED_CARVER,
            net.minecraft.resources.ResourceLocation.parse("minecraft:cave_extra_underground")));
        var canyonCarver = carverReg.getOrThrow(net.minecraft.resources.ResourceKey.create(
            net.minecraft.core.registries.Registries.CONFIGURED_CARVER,
            net.minecraft.resources.ResourceLocation.parse("minecraft:canyon")));

        java.util.List<net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver<?>> carvers =
            java.util.List.of(caveCarver.value(), caveExtraCarver.value(), canyonCarver.value());

        net.minecraft.world.level.chunk.CarvingMask carvingMask =
            ((ProtoChunk) chunk).getOrCreateCarvingMask(net.minecraft.world.level.levelgen.GenerationStep.Carving.AIR);

        net.minecraft.world.level.levelgen.WorldgenRandom worldgenRandom =
            new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));

        java.util.function.Function<net.minecraft.core.BlockPos, net.minecraft.core.Holder<Biome>> biomeAccessor =
            pos -> biomeSource.getNoiseBiome(pos.getX() >> 2, 0, pos.getZ() >> 2, randomState.sampler());

        // Track who carves (0, 2, 12) for seed=0; (13, 18, 8) for seed=123456789
        net.minecraft.core.BlockPos trackPos = (seed == 0 && cx == 0 && cz == 0)
            ? new net.minecraft.core.BlockPos(0, 2, 12)
            : (seed == 123456789L && cx == 0 && cz == 0)
            ? new net.minecraft.core.BlockPos(13, 18, 8)
            : null;
        net.minecraft.world.level.block.state.BlockState trackBefore = trackPos != null ? chunk.getBlockState(trackPos) : null;
        // Multi-track: all y=2..3 blocks in lz=12..15 for seed=0 cx=0 cz=0
        boolean multiTrack = (seed == 0 && cx == 0 && cz == 0);
        java.util.Map<net.minecraft.core.BlockPos, net.minecraft.world.level.block.state.BlockState> multiTrackBefore = new java.util.LinkedHashMap<>();
        if (multiTrack) {
            for (int mty = 2; mty <= 3; mty++)
                for (int mtz = 12; mtz <= 15; mtz++)
                    for (int mtx = 0; mtx <= 6; mtx++) {
                        net.minecraft.core.BlockPos mp = new net.minecraft.core.BlockPos(mtx, mty, mtz);
                        multiTrackBefore.put(mp, chunk.getBlockState(mp));
                    }
        }

        for (int originX = cx - 8; originX <= cx + 8; originX++) {
            for (int originZ = cz - 8; originZ <= cz + 8; originZ++) {
                ChunkPos originPos = new ChunkPos(originX, originZ);
                int carverIndex = 0;
                for (var carver : carvers) {
                    worldgenRandom.setLargeFeatureSeed(seed + carverIndex, originX, originZ);
                    boolean starts = carver.isStartChunk(worldgenRandom);
                    if (originX == 0 && originZ == -1 && seed == 123456789L) {
                        System.out.printf("DBG_ORA origin(0,-1) carver=%s starts=%b%n",
                            carver.getClass().getSimpleName(), starts);
                    }
                    // Debug canyon at origin(5,-5) for seed=123456789 (we over-carve there)
                    if (originX == 5 && originZ == -5 && seed == 123456789L) {
                        System.out.printf("DBG_ORA origin(5,-5) carver=%s starts=%b%n",
                            carver.getClass().getSimpleName(), starts);
                    }
                    // Print canyon params for origin(5,-5) seed=123456789 by replaying random
                    if (originX == 5 && originZ == -5 && seed == 123456789L && starts
                            && carver.config() instanceof net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration) {
                        net.minecraft.world.level.levelgen.WorldgenRandom snap =
                            new net.minecraft.world.level.levelgen.WorldgenRandom(
                                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
                        snap.setLargeFeatureSeed(seed, 5, -5);
                        snap.nextFloat(); // isStartChunk
                        int xRaw = snap.nextInt(16);
                        int yRaw = snap.nextInt(58); // uniform(10,67) = nextInt(58)+10
                        int zRaw = snap.nextInt(16);
                        float yaw = snap.nextFloat() * 6.2831855f;
                        float vRot = -0.125f + snap.nextFloat() * 0.25f; // uniform(-0.125, 0.125)
                        // yScale = constant 3.0 (no random)
                        float trapH = snap.nextFloat();
                        net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration canyonCfg =
                            (net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration) carver.config();
                        // Compute thickness from vanilla TrapezoidFloat
                        net.minecraft.util.valueproviders.TrapezoidFloat thickProvider =
                            (net.minecraft.util.valueproviders.TrapezoidFloat) canyonCfg.shape.thickness;
                        // Since we can't call sample() with a fixed h, let's print the raw float
                        float distH = snap.nextFloat();
                        long tSeed = snap.nextLong();
                        System.out.printf("DBG_ORA_CYN origin(5,-5) xRaw=%d yRaw=%d(%d) zRaw=%d yaw=%.6f vRot=%.6f trapH=%.8f distH=%.8f tunnelSeed=%d%n",
                            xRaw, yRaw, 5*16+xRaw, zRaw, yaw, vRot, trapH, distH, tSeed);
                        System.out.printf("DBG_ORA_CYN world: x=%.1f y=%d z=%.1f%n", 5.0*16+xRaw, 10+yRaw, -5.0*16+zRaw);
                    }
                    // Debug canyon at origin(3,0) for seed=0 (oracle carves (0,2,12) but we don't)
                    if (originX == 3 && originZ == 0 && seed == 0L) {
                        System.out.printf("DBG_ORA origin(3,0) carver=%s starts=%b%n",
                            carver.getClass().getSimpleName(), starts);
                    }
                    // Simulate canyon path for seed=0 origin(3,0) to trace where oracle's path goes
                    if (originX == 3 && originZ == 0 && seed == 0L && starts
                            && carver.config() instanceof net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration canyonCfg0) {
                        // Replay random using ACTUAL config methods to exactly match vanilla's carve() random consumption
                        net.minecraft.world.level.levelgen.WorldgenRandom snap0 =
                            new net.minecraft.world.level.levelgen.WorldgenRandom(
                                new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
                        snap0.setLargeFeatureSeed(seed, 3, 0);
                        snap0.nextFloat(); // isStartChunk
                        double sx0 = (double) new net.minecraft.world.level.ChunkPos(3, 0).getBlockX(snap0.nextInt(16));
                        int sy0 = canyonCfg0.y.sample(snap0, carvingCtx);  // use actual config method
                        double sz0 = (double) new net.minecraft.world.level.ChunkPos(3, 0).getBlockZ(snap0.nextInt(16));
                        float yaw0 = snap0.nextFloat() * (float)(Math.PI * 2);
                        float vRot0 = canyonCfg0.verticalRotation.sample(snap0);  // actual provider
                        double yScaleD = (double) canyonCfg0.yScale.sample(snap0);   // actual provider
                        float thickness0 = canyonCfg0.shape.thickness.sample(snap0); // actual provider
                        int maxStep0 = (int)((float)240 * canyonCfg0.shape.distanceFactor.sample(snap0)); // 240=(getRange()*2-1)*16, getRange()=8 for canyon
                        long tSeed0 = snap0.nextLong();
                        System.out.printf("DBG_SIM0 origin(3,0) x=%.1f y=%d z=%.1f yaw=%.6f vRot=%.6f yScale=%.4f thick=%.6f maxStep=%d tSeed=%d%n",
                            sx0, sy0, sz0, yaw0, vRot0, yScaleD, thickness0, maxStep0, tSeed0);
                        // Simulate doCarve path using vanilla LegacyRandom
                        net.minecraft.world.level.levelgen.LegacyRandomSource br0 =
                            new net.minecraft.world.level.levelgen.LegacyRandomSource(tSeed0);
                        // initWidthFactors — use actual config values
                        int genDepth0 = carvingCtx.getGenDepth();
                        float[] wf0 = new float[genDepth0];
                        float f0 = 1.0f;
                        for (int i0 = 0; i0 < genDepth0; i0++) {
                            if (i0 == 0 || br0.nextInt(canyonCfg0.shape.widthSmoothness) == 0) {
                                f0 = 1.0f + br0.nextFloat() * br0.nextFloat();
                            }
                            wf0[i0] = f0 * f0;
                        }
                        System.out.printf("SIM_WF wf[65]=%.6f wf[66]=%.6f (by=2 idx=65, by=3 idx=66)%n", wf0[65], wf0[66]);
                        // Now simulate step loop — hDistBase as double (matches vanilla/Furnace)
                        double bx0 = sx0, by0 = sy0, bz0 = sz0;
                        float pitch0 = vRot0, yawV0 = yaw0, pitchDir0 = 0, yawDir0 = 0;
                        int minY0 = -64;
                        for (int step0 = 0; step0 < maxStep0; step0++) {
                            // 1. hDist/vDist — use actual config sample methods via br0
                            double hDistBase0 = 1.5 + (double)(net.minecraft.util.Mth.sin((float)step0 * (float)Math.PI / (float)maxStep0) * thickness0);
                            double eVert0 = hDistBase0 * yScaleD;
                            double hDist0 = hDistBase0 * (double)canyonCfg0.shape.horizontalRadiusFactor.sample(br0);
                            // updateVerticalRadius: vrDefaultFactor + vrCenterFactor * f, then * Mth.randomBetween(br0, 0.75, 1.0)
                            float vrf0 = canyonCfg0.shape.verticalRadiusDefaultFactor + canyonCfg0.shape.verticalRadiusCenterFactor * (1.0f - net.minecraft.util.Mth.abs(0.5f - (float)step0/(float)maxStep0)*2.0f);
                            double vDist0 = (double)vrf0 * eVert0 * (double)net.minecraft.util.Mth.randomBetween(br0, 0.75f, 1.0f);
                            // 2. Position update
                            float cosp0 = net.minecraft.util.Mth.cos(pitch0);
                            bx0 += net.minecraft.util.Mth.cos(yawV0) * cosp0;
                            by0 += net.minecraft.util.Mth.sin(pitch0);
                            bz0 += net.minecraft.util.Mth.sin(yawV0) * cosp0;
                            // 3. Pitch/yaw decay + random
                            pitch0 *= 0.7f; pitch0 += pitchDir0 * 0.05f;
                            yawV0 += yawDir0 * 0.05f;
                            pitchDir0 *= 0.8f; yawDir0 *= 0.5f;
                            pitchDir0 += (br0.nextFloat() - br0.nextFloat()) * br0.nextFloat() * 2.0f;
                            yawDir0 += (br0.nextFloat() - br0.nextFloat()) * br0.nextFloat() * 4.0f;
                            // 4. Skip check
                            int skip0 = br0.nextInt(4);
                            // Compute yMin like Furnace
                            int yMinRaw0 = (int)Math.floor(by0 - vDist0) - 1;
                            int yMin0 = Math.max(minY0 + 1, yMinRaw0);
                            // Print ALL steps with yMin<=1 (can reach y=2), or yMin<=3 near chunk 0
                            // genDepth sanity: print once
                            if (step0 == 0) System.out.printf("SIM_GDEPTH genDepth=%d wSmooth=%d%n", genDepth0, canyonCfg0.shape.widthSmoothness);
                            boolean nearChunk0 = bx0 >= -5 && bx0 <= 17 && bz0 >= 10 && bz0 <= 18;
                            if ((yMin0 <= 1) || (yMin0 <= 4 && nearChunk0)) {
                                System.out.printf("SIM_LOW step=%d center=(%.3f,%.3f,%.3f) hDist=%.4f vDist=%.4f yMin=%d skip=%d%n",
                                        step0, bx0, by0, bz0, hDist0, vDist0, yMin0, skip0 == 0 ? 1 : 0);
                                // When yMin<=1 (loop can reach y=2), check if block (0,2,12) passes skipCheck
                                if (yMin0 <= 1 && hDist0 > 0 && skip0 != 0) {
                                    double relX02 = (0.5 - bx0) / hDist0;
                                    double relZ02 = (12.5 - bz0) / hDist0;
                                    double hz0 = relX02*relX02 + relZ02*relZ02;
                                    if (hz0 < 1.0) {
                                        double relY02 = (2 - 0.5 - by0) / vDist0;
                                        double skipVal0 = hz0 * wf0[65] + relY02*relY02/6.0;
                                        System.out.printf("  -> (0,2,12) relX=%.3f relZ=%.3f hz=%.3f relY=%.3f skipVal=%.3f %s%n",
                                            relX02, relZ02, hz0, relY02, skipVal0, skipVal0 < 1.0 ? "CARVED" : "skipped");
                                    } else {
                                        System.out.printf("  -> (0,2,12) OUTSIDE horizontal: hz=%.3f%n", hz0);
                                    }
                                }
                            }
                        }
                    }
                    if (starts) {
                        if (originX == 0 && originZ == -1
                                && carver.config() instanceof net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration caveCfg
                                && caveCfg.probability == 0.15f) {
                            // Snapshot random state after isStartChunk consumed 1 float
                            net.minecraft.world.level.levelgen.WorldgenRandom snap = new net.minecraft.world.level.levelgen.WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(0));
                            snap.setLargeFeatureSeed(seed, 0, -1);
                            snap.nextFloat(); // isStartChunk
                            // count cascade: nextInt(nextInt(nextInt(15)+1)+1) = nextInt(nextInt(R1+1)+1) = nextInt(R2+1) = count
                            int R1 = snap.nextInt(15); int a = R1 + 1;
                            int R2 = snap.nextInt(a);  int b = R2 + 1;
                            int cnt = snap.nextInt(b);
                            int sx16 = snap.nextInt(16); // startX candidate (i=0)
                            System.out.printf("DBG_ORA_CAVE origin(0,-1) a=%d b=%d count=%d startX_raw=%d%n", a, b, cnt, sx16);
                            // Now simulate i=0 to get to i=1
                            // i=0: we already consumed startX. continue: startY, startZ, hRad, vRad, floor, room?
                            snap.nextInt(237); // startY = nextInt(237) for [-56,180]
                            snap.nextInt(16);  // startZ
                            snap.nextFloat();  // hRadMult
                            snap.nextFloat();  // vRadMult
                            snap.nextFloat();  // floorLevel
                            int room0 = snap.nextInt(4);
                            if (room0 == 0) {
                                snap.nextFloat(); // yScale
                                snap.nextFloat(); // roomThick
                                snap.nextInt(4);  // tunnelCount bonus
                            }
                            // tunnel 0: yaw, pitch, thickness
                            snap.nextFloat(); // yaw
                            snap.nextFloat(); // pitch
                            float t0 = snap.nextFloat() * 2.0f + snap.nextFloat(); // thickness base
                            if (snap.nextInt(10) == 0) { snap.nextFloat(); snap.nextFloat(); } // thickness rare
                            snap.nextInt(28); // maxStep variance
                            long seed1 = snap.nextLong(); // tunnel seed for i=0 t=0
                            System.out.printf("DBG_ORA_CAVE i=0 tunnelSeed=%d%n", seed1);
                            // i=1: startX, startY, startZ, hRad, vRad, floor
                            int i1sx = snap.nextInt(16); // startX_raw for i=1
                            int i1sy = snap.nextInt(237); // startY_raw
                            int i1sz = snap.nextInt(16);  // startZ_raw
                            float i1hrad = 0.7f + snap.nextFloat() * 0.7f;
                            float i1vrad = 0.8f + snap.nextFloat() * 0.5f;
                            float i1fl = -1.0f + snap.nextFloat() * 0.6f;
                            System.out.printf("DBG_ORA_CAVE i=1 startX_raw=%d startY_raw=%d(%d) startZ_raw=%d%n", i1sx, i1sy, -56+i1sy, i1sz);
                            int room1 = snap.nextInt(4);
                            if (room1 == 0) { snap.nextFloat(); snap.nextFloat(); snap.nextInt(4); }
                            snap.nextFloat(); // yaw
                            snap.nextFloat(); // pitch
                            float t1 = snap.nextFloat() * 2.0f + snap.nextFloat();
                            if (snap.nextInt(10) == 0) { snap.nextFloat(); snap.nextFloat(); }
                            snap.nextInt(28);
                            long seed2 = snap.nextLong();
                            System.out.printf("DBG_ORA_CAVE i=1 tunnelSeed=%d%n", seed2);
                        }
                        // Check carvingMask at (3,-47,0) before and after carve (local x=3, y=-47, local z=0)
                        boolean maskBefore = carvingMask.get(3, -47, 0);
                        // Before cave_extra_underground(0,-1): re-check aquifer and lavaLevel
                        if (originX == 0 && originZ == -1
                                && carver.config() instanceof net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration cc2
                                && Math.abs(cc2.probability - 0.07f) < 0.001f) {
                            net.minecraft.world.level.block.state.BlockState aqPre = aquifer.computeSubstance(
                                new DensityFunction.SinglePointContext(3, -47, 0), 0.0);
                            System.out.printf("DBG_AQ_PRE cave_extra aquifer(3,-47,0) → %s%n", aqPre);
                            System.out.printf("DBG_LAVA carvingCtx.getMinGenY()=%d lavaLevel=%d%n",
                                carvingCtx.getMinGenY(),
                                cc2.lavaLevel.resolveY(carvingCtx));
                            // Check replaceable tag for deepslate
                            net.minecraft.core.BlockPos chkPos = new net.minecraft.core.BlockPos(3, -47, 0);
                            net.minecraft.world.level.block.state.BlockState chkState = chunk.getBlockState(chkPos);
                            boolean chkRepl = chkState.is(cc2.replaceable);
                            System.out.printf("DBG_REPL block(3,-47,0)=%s is(replaceable)=%b tag=%s%n", chkState, chkRepl, cc2.replaceable);
                            // Scan y=-50 to -44 after carve to see what changed
                        }
                        // Snapshot before origin(3,0) canyon for seed=0
                        boolean snapOrigin30 = (seed == 0L && originX == 3 && originZ == 0
                            && carver.config() instanceof net.minecraft.world.level.levelgen.carver.CanyonCarverConfiguration);
                        net.minecraft.world.level.block.state.BlockState[] snap30 = null;
                        if (snapOrigin30) {
                            snap30 = new net.minecraft.world.level.block.state.BlockState[16 * 384 * 16];
                            int idx30 = 0;
                            for (int sy = -64; sy < 320; sy++) {
                                for (int sz = 0; sz < 16; sz++) {
                                    for (int sx = 0; sx < 16; sx++) {
                                        snap30[idx30++] = chunk.getBlockState(new net.minecraft.core.BlockPos(sx, sy, sz));
                                    }
                                }
                            }
                        }
                        // For origin(3,0) canyon seed=0: use logging mask to trace low-y set() calls
                        net.minecraft.world.level.chunk.CarvingMask activeCarvePhase = carvingMask;
                        if (snapOrigin30) {
                            activeCarvePhase = new net.minecraft.world.level.chunk.CarvingMask(carvingCtx.getGenDepth(), carvingCtx.getMinGenY()) {
                                @Override public void set(int offsetX, int y, int offsetZ) {
                                    if (y <= 5) System.out.printf("ORA_SET lx=%d y=%d lz=%d%n", offsetX, y, offsetZ);
                                    super.set(offsetX, y, offsetZ);
                                    carvingMask.set(offsetX, y, offsetZ);
                                }
                                @Override public boolean get(int offsetX, int y, int offsetZ) {
                                    return super.get(offsetX, y, offsetZ) || carvingMask.get(offsetX, y, offsetZ);
                                }
                            };
                        }
                        carver.carve(carvingCtx, chunk, biomeAccessor, worldgenRandom, aquifer, originPos, activeCarvePhase);
                        // Print all blocks carved by origin(3,0) canyon for seed=0
                        if (snapOrigin30 && snap30 != null) {
                            int idx30 = 0;
                            for (int sy = -64; sy < 320; sy++) {
                                for (int sz = 0; sz < 16; sz++) {
                                    for (int sx = 0; sx < 16; sx++) {
                                        net.minecraft.world.level.block.state.BlockState after30 = chunk.getBlockState(new net.minecraft.core.BlockPos(sx, sy, sz));
                                        if (!after30.equals(snap30[idx30])) {
                                            System.out.printf("SNAP30 (%d,%d,%d): %s->%s%n", sx, sy, sz, snap30[idx30], after30);
                                        }
                                        idx30++;
                                    }
                                }
                            }
                        }
                        // Track (0,2,12) changes for seed=0 debugging
                        if (trackPos != null) {
                            net.minecraft.world.level.block.state.BlockState trackAfter = chunk.getBlockState(trackPos);
                            if (!trackAfter.equals(trackBefore)) {
                                System.out.printf("TRK_CARVED (0,2,12): origin=(%d,%d) carver=%s config=%s %s->%s%n",
                                    originX, originZ, carver.getClass().getSimpleName(),
                                    carver.config().getClass().getSimpleName(), trackBefore, trackAfter);
                                trackBefore = trackAfter;
                            }
                        }
                        // Multi-track: report any y=2..3 block that just changed
                        if (multiTrack) {
                            for (var mte : multiTrackBefore.entrySet()) {
                                net.minecraft.world.level.block.state.BlockState after = chunk.getBlockState(mte.getKey());
                                if (!after.equals(mte.getValue())) {
                                    net.minecraft.core.BlockPos mp = mte.getKey();
                                    System.out.printf("TRK_LOW (%d,%d,%d): origin=(%d,%d) carver=%s %s->%s%n",
                                        mp.getX(), mp.getY(), mp.getZ(), originX, originZ,
                                        carver.config().getClass().getSimpleName().replace("CarverConfiguration",""),
                                        mte.getValue(), after);
                                    mte.setValue(after);
                                }
                            }
                        }
                        // After carve, check block at (3,-47,0)
                        if (originX == 0 && originZ == -1 && seed == 123456789L) {
                            net.minecraft.world.level.block.state.BlockState b347 = chunk.getBlockState(
                                new net.minecraft.core.BlockPos(3, -47, 0));
                            boolean maskAfter = carvingMask.get(3, -47, 0);
                            System.out.printf("DBG_ORA after carver(0,-1) block(3,-47,0)=%s maskBefore=%b maskAfter=%b%n", b347, maskBefore, maskAfter);
                            // If cave_extra, scan y range around -47
                            if (carver.config() instanceof net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration cc3
                                    && Math.abs(cc3.probability - 0.07f) < 0.001f) {
                                for (int sy = -52; sy <= -42; sy++) {
                                    net.minecraft.world.level.block.state.BlockState sy_state = chunk.getBlockState(new net.minecraft.core.BlockPos(3, sy, 0));
                                    System.out.printf("DBG_SCAN y=%d block=%s mask=%b%n", sy, sy_state, carvingMask.get(3, sy, 0));
                                }
                                // Also check aquifer post-carve
                                net.minecraft.world.level.block.state.BlockState aqPost = aquifer.computeSubstance(
                                    new DensityFunction.SinglePointContext(3, -47, 0), 0.0);
                                System.out.printf("DBG_AQ_POST cave_extra aquifer(3,-47,0) → %s%n", aqPost);
                            }
                        }
                    }
                    carverIndex++;
                }
            }
        }

        // DBG: dump column x=0,z=12 from y=-2 to y=25 to see oracle carve depth
        if (seed == 0L && cx == 0 && cz == 0) {
            System.out.println("DBG_COL x=0,z=12:");
            for (int dy = 25; dy >= -2; dy--) {
                BlockState s = chunk.getBlockState(new net.minecraft.core.BlockPos(0, dy, 12));
                System.out.printf("  y=%-3d %s%n", dy, s);
            }
        }

        // 6. Dump blocks as uint16 state IDs
        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        int headerSize  = 4 + 8 + 4 + 4;
        ByteBuffer buf  = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x43525652); // "CRVR" magic
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    short id = (short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state);
                    buf.putShort(id);
                }
            }
        }

        Path out = outDir.resolve("carver_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
    }

    private static void dumpFeatureFullChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        RegistryAccess registryAccess,
        HolderLookup.Provider registries,
        Path outDir
    ) throws Exception {
        ProtoChunk[] grid = new ProtoChunk[9];
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                grid[(dz + 1) * 3 + (dx + 1)] = buildCarvedProtoChunk(
                    seed, cx + dx, cz + dz, generator, randomState, settings,
                    fluidPicker, biomeRegistry, biomeSource, registryAccess);
            }
        }

        BlockState[] before = snapshotChunk(grid[4], cx, cz);

        OracleWorldGenLevel level = new OracleWorldGenLevel(
            grid, cx, cz, seed, biomeSource, randomState, registryAccess, registries);

        // 디버그: -Doracle.dumpColumns="x,z[,yFrom,yTo];..." — decoration 전 컬럼 상태·heightmap 출력
        String dumpColumns = System.getProperty("oracle.dumpColumns");
        if (dumpColumns != null) {
            for (String pair : dumpColumns.split(";")) {
                String[] xz = pair.split(",");
                int wx = Integer.parseInt(xz[0].trim());
                int wz = Integer.parseInt(xz[1].trim());
                int yFrom;
                int yTo;
                if (xz.length >= 4) {
                    yFrom = Integer.parseInt(xz[2].trim());
                    yTo = Integer.parseInt(xz[3].trim());
                } else {
                    int h = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.OCEAN_FLOOR, wx, wz);
                    System.out.printf("ORACLE_COL wx=%d wz=%d oceanFloorHeight=%d%n", wx, wz, h);
                    yFrom = h - 4;
                    yTo = h + 3;
                }
                for (int y = yFrom; y <= yTo; y++) {
                    BlockState st = level.getBlockState(new net.minecraft.core.BlockPos(wx, y, wz));
                    System.out.printf("ORACLE_COL wx=%d y=%d wz=%d state=%s fluid=%s sturdyUp=%s%n",
                        wx, y, wz, st,
                        st.getFluidState().is(net.minecraft.tags.FluidTags.WATER) ? "water" + st.getFluidState().getAmount() : st.getFluidState(),
                        st.isFaceSturdy(level, new net.minecraft.core.BlockPos(wx, y, wz), net.minecraft.core.Direction.UP));
                }
            }
        }

        placeVanillaFeatures(level, grid[4], generator, biomeSource, registryAccess);

        int changed = 0;
        int idx = 0;
        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = grid[4].getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    if (!state.equals(before[idx++])) {
                        changed++;
                    }
                }
            }
        }

        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        int headerSize = 4 + 8 + 4 + 4;
        ByteBuffer buf = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x46464C42); // "FFLB"
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = grid[4].getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    buf.putShort((short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state));
                }
            }
        }

        Path out = outDir.resolve("featurefull_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
        System.out.printf("carver→featurefull 블록 변화 수: %d%n", changed);
    }

    /**
     * featurefull의 반쪽 의미(center origin만 decorate) 버그를 드러내는 fixture.
     * 5×5 carved grid를 만들고 안쪽 3×3 origin 9개를 전부(vanilla applyBiomeDecoration 미러, 구조물 없음 —
     * featurefull과 동일하게 structureManager=null) decorate한 뒤 center만 덤프한다. 이웃 origin decoration이
     * center로 넘어오는 spill(나무 캐노피 등)이 포함된다는 점이 featurefull과의 유일한 차이.
     * origin 순회 순서는 dz 오름차순→dx 오름차순(행 우선)으로 고정 — ChunkPipeline.generateFull과 반드시 동일해야
     * byte-exact가 성립한다(진짜 바닐라의 청크 로드 순서는 런타임 의존적이라 재현 불가 — Issues 참조).
     */
    private static void dumpFeatureSpillChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        RegistryAccess registryAccess,
        HolderLookup.Provider registries,
        Path outDir
    ) throws Exception {
        final int radius = 2;
        final int size = 2 * radius + 1;
        ProtoChunk[] grid = new ProtoChunk[size * size];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                grid[(dz + radius) * size + (dx + radius)] = buildCarvedProtoChunk(
                    seed, cx + dx, cz + dz, generator, randomState, settings,
                    fluidPicker, biomeRegistry, biomeSource, registryAccess);
            }
        }

        int centerIdx = radius * size + radius;
        BlockState[] before = snapshotChunk(grid[centerIdx], cx, cz);

        OracleWorldGenLevel level = new OracleWorldGenLevel(
            grid, cx, cz, radius, seed, biomeSource, randomState, registryAccess, registries);

        for (int ddz = -1; ddz <= 1; ddz++) {
            for (int ddx = -1; ddx <= 1; ddx++) {
                int ox = cx + ddx;
                int oz = cz + ddz;
                ProtoChunk originChunk = grid[(ddz + radius) * size + (ddx + radius)];
                level.setDecorationOrigin(ox, oz);
                placeVanillaFeatures(level, originChunk, generator, biomeSource, registryAccess);
            }
        }

        int changed = 0;
        int idx = 0;
        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = grid[centerIdx].getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    if (!state.equals(before[idx++])) {
                        changed++;
                    }
                }
            }
        }

        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        int headerSize = 4 + 8 + 4 + 4;
        ByteBuffer buf = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x46534C42); // "FSLB" — featurefull(FFLB)/featurestruct(SFLB)와 구분되는 신규 매직
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = grid[centerIdx].getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    buf.putShort((short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state));
                }
            }
        }

        Path out = outDir.resolve("featurespill_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
        System.out.printf("carver→featurespill 블록 변화 수: %d%n", changed);
    }

    /**
     * featurefull과 동일하되 실제 StructureManager+ChunkGeneratorStructureState로
     * createStructures→createReferences(17x17, radius 8)→applyBiomeDecoration(구조물 postProcess 포함)을
     * 실행한다. Beardifier는 여전히 EMPTY(F9 — beardify는 이 마일스톤 범위 밖).
     */
    private static void dumpFeatureStructChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        RegistryAccess registryAccess,
        HolderLookup.Provider registries,
        Path outDir
    ) throws Exception {
        final int radius = 8;
        final int size = 2 * radius + 1;
        ProtoChunk[] grid = new ProtoChunk[size * size];
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                grid[(dz + radius) * size + (dx + radius)] = buildCarvedProtoChunk(
                    seed, cx + dx, cz + dz, generator, randomState, settings,
                    fluidPicker, biomeRegistry, biomeSource, registryAccess);
            }
        }

        int centerIdx = radius * size + radius;
        BlockState[] before = snapshotChunk(grid[centerIdx], cx, cz);

        OracleWorldGenLevel level = new OracleWorldGenLevel(
            grid, cx, cz, radius, seed, biomeSource, randomState, registryAccess, registries);

        // 실 StructureManager 구성 — 스크래치 저장소(디스크에 실제 청크 저장 없음, DirectoryLock만 생성)
        //
        // (가정/이슈) Paper 서버 클래스패스에서는 ChunkGeneratorStructureState.createForNormal이
        // 5번째 인자로 org.spigotmc.SpigotWorldConfig를 요구한다(순정 vanilla엔 없음, Paper 추가 파라미터).
        // SpigotWorldConfig 생성자는 즉시 init()을 호출하고, init()은 정적 SpigotConfig.config(YamlConfiguration)를
        // 읽는데 이 헤드리스 오라클 환경엔 실 Bukkit 서버가 없어 그 static이 null → NPE.
        // 우회: createForNormal(공개 팩토리, injectSpigot 경유)을 쓰지 않고 private 전체-인자 생성자를
        // 리플렉션으로 직접 호출해 SpigotWorldConfig 관여(=injectSpigot의 seed override) 자체를 건너뛴다.
        // possibleStructureSets도 desert_pyramids 단일 set으로 제한 — Furnace가 구현한 유일한 구조물이므로
        // (S4+ 범위인 shipwrecks 등 다른 set의 salt가 SpigotWorldConfig 미초기화로 오염되는 것도 함께 방지).
        net.minecraft.core.HolderLookup<net.minecraft.world.level.levelgen.structure.StructureSet> structureSetLookup =
            registries.lookupOrThrow(Registries.STRUCTURE_SET);
        net.minecraft.core.Holder<net.minecraft.world.level.levelgen.structure.StructureSet> desertPyramidsSet =
            structureSetLookup.get(net.minecraft.resources.ResourceKey.create(Registries.STRUCTURE_SET,
                net.minecraft.resources.ResourceLocation.parse("minecraft:desert_pyramids"))).orElseThrow();

        // (S3) datagen-mode 트랩: structure.biomes()의 #has_structure/desert_pyramid 태그(HolderSet.Named)가
        // 바인딩되지 않아 isValidBiome이 전부 false → 어떤 청크에서도 start가 안 생겼다(F-신규, S2c 픽스처 (a)는
        // start 없는 청크라 이 경로가 한 번도 검증 안 됨). biomeSource가 실제로 반환하는 desert holder 인스턴스로
        // 태그를 리플렉션 bind해 실서버(태그 로드됨)와 동일하게 만든다.
        net.minecraft.world.level.levelgen.structure.Structure pyramidStructure =
            desertPyramidsSet.value().structures().get(0).structure().value();
        {
            net.minecraft.core.HolderSet<Biome> biomesSet = pyramidStructure.biomes();
            if (biomesSet instanceof net.minecraft.core.HolderSet.Named<Biome> named) {
                // HolderSet.Named.contains(holder)는 holder.is(tagKey)로 위임 → holder 쪽 tags에 바인딩해야 한다.
                java.util.List<Holder<Biome>> desertHolders = new java.util.ArrayList<>();
                for (Holder<Biome> h : biomeSource.possibleBiomes()) {
                    if (h.is(net.minecraft.world.level.biome.Biomes.DESERT)) desertHolders.add(h);
                }
                desertHolders.add(registries.lookupOrThrow(Registries.BIOME)
                    .getOrThrow(net.minecraft.world.level.biome.Biomes.DESERT));
                java.lang.reflect.Method bind = net.minecraft.core.HolderSet.Named.class
                    .getDeclaredMethod("bind", java.util.List.class);
                bind.setAccessible(true);
                bind.invoke(named, desertHolders);
                java.lang.reflect.Method bindTags = net.minecraft.core.Holder.Reference.class
                    .getDeclaredMethod("bindTags", java.util.Collection.class);
                bindTags.setAccessible(true);
                for (Holder<Biome> h : desertHolders) {
                    if (h instanceof Holder.Reference<Biome> ref) {
                        bindTags.invoke(ref, java.util.Set.of(named.key()));
                    }
                }
                System.out.printf("ORACLE_TAGBIND named=%s bound=%d%n", named.key(), desertHolders.size());
            } else {
                System.out.printf("ORACLE_TAGBIND skip class=%s%n", biomesSet.getClass().getName());
            }
        }

        java.lang.reflect.Constructor<net.minecraft.world.level.chunk.ChunkGeneratorStructureState> stateCtor =
            net.minecraft.world.level.chunk.ChunkGeneratorStructureState.class.getDeclaredConstructor(
                RandomState.class, net.minecraft.world.level.biome.BiomeSource.class, long.class, long.class,
                java.util.List.class, org.spigotmc.SpigotWorldConfig.class);
        stateCtor.setAccessible(true);
        net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState = stateCtor.newInstance(
            randomState, biomeSource, seed, seed, java.util.List.of(desertPyramidsSet), null);

        // fixture 출력 디렉터리(outDir)가 아니라 시스템 임시 폴더에 스크래치 락파일을 둔다 —
        // StructureTemplateManager 생성자가 요구하는 LevelStorageAccess용, 실제 청크 저장 안 함.
        Path scratchSaves = Files.createTempDirectory("furnace_oracle_scratch");
        net.minecraft.world.level.storage.LevelStorageSource storageSource =
            net.minecraft.world.level.storage.LevelStorageSource.createDefault(scratchSaves);
        net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess levelStorageAccess =
            storageSource.createAccess("oracle_featurestruct",
                net.minecraft.world.level.dimension.LevelStem.OVERWORLD);

        com.mojang.datafixers.DataFixer dataFixer = net.minecraft.util.datafix.DataFixers.getDataFixer();
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager =
            new net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager(
                net.minecraft.server.packs.resources.ResourceManager.Empty.INSTANCE,
                levelStorageAccess, dataFixer, registryAccess.lookupOrThrow(Registries.BLOCK));

        net.minecraft.world.level.chunk.storage.ChunkScanAccess scanStub =
            (chunkPos, visitor) -> java.util.concurrent.CompletableFuture.completedFuture(null);
        net.minecraft.world.level.levelgen.structure.StructureCheck structureCheck =
            new net.minecraft.world.level.levelgen.structure.StructureCheck(
                scanStub, registryAccess, templateManager, net.minecraft.world.level.dimension.LevelStem.OVERWORLD,
                generator, randomState, level, biomeSource, seed, dataFixer);

        net.minecraft.world.level.levelgen.WorldOptions worldOptions =
            new net.minecraft.world.level.levelgen.WorldOptions(seed, true, false);
        net.minecraft.world.level.StructureManager structureManager =
            new net.minecraft.world.level.StructureManager(level, worldOptions, structureCheck);

        for (ProtoChunk chunk : grid) {
            // vanilla generator.createStructures(...) 대신 직접 미러링한다 — Paper 패치가
            // tryGenerateStructure 안에서 실 Bukkit 서버 필요한 AsyncStructureSpawnEvent를 무조건
            // 발사하는데(structureAccessor.level.getMinecraftWorld().getWorld() 호출) 이 헤드리스
            // 오라클엔 Bukkit.setServer()된 서버가 없어 유효한 StructureStart가 하나라도 생기면
            // NPE로 죽는다(S3 태그바인드 수정 전엔 isValidBiome이 항상 false라 이 경로를 탄 적이 없어서
            // 미발견). 플러그인 리스너가 없는 환경이므로 이벤트 미발사는 취소되지 않은 기본 동작과 동일.
            createStructuresNoBukkitEvent(registryAccess, structureState, structureManager, chunk, templateManager, generator);
        }
        // createReferences(center)만 실행 — StructureManager.startsForStructure(centerSectionPos, ..)는
        // center 청크 자신의 참조 집합만 읽으므로(StructureManager.java:64-71) 이웃 청크에서 createReferences를
        // 또 돌릴 필요 없다. 모든 289개 청크에 대해 돌리면 그 청크의 ±8 추가 조회가 radius=8 grid 밖으로 나가 실패한다.
        generator.createReferences(level, structureManager, grid[centerIdx]);

        placeVanillaFeatures(level, grid[centerIdx], generator, biomeSource, registryAccess, structureManager, registries, pyramidStructure);

        int changed = 0;
        int idx = 0;
        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = grid[centerIdx].getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    if (!state.equals(before[idx++])) {
                        changed++;
                    }
                }
            }
        }

        int totalBlocks = FixtureFormat.HEIGHT * 16 * 16;
        int headerSize = 4 + 8 + 4 + 4;
        ByteBuffer buf = ByteBuffer.allocate(headerSize + totalBlocks * 2).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x53464C42); // "SFLB" (structure feature label block)
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);

        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    int worldX = cx * 16 + localX;
                    int worldZ = cz * 16 + localZ;
                    BlockState state = grid[centerIdx].getBlockState(new net.minecraft.core.BlockPos(worldX, y, worldZ));
                    buf.putShort((short) net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.getId(state));
                }
            }
        }

        Path out = outDir.resolve("featurestruct_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes)%n", out, buf.capacity());
        System.out.printf("carver→featurestruct 블록 변화 수: %d%n", changed);
    }

    /** vanilla ChunkGenerator.createStructures 미러 (CraftBukkit AsyncStructureSpawnEvent 생략 — 근거는 위 호출부 주석). */
    private static void createStructuresNoBukkitEvent(
        RegistryAccess registryAccess,
        net.minecraft.world.level.chunk.ChunkGeneratorStructureState structureState,
        net.minecraft.world.level.StructureManager structureManager,
        ProtoChunk chunk,
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
        NoiseBasedChunkGenerator generator
    ) {
        ChunkPos chunkPos = chunk.getPos();
        SectionPos sectionPos = SectionPos.bottomOf(chunk);
        RandomState randomState = structureState.randomState();

        for (Holder<net.minecraft.world.level.levelgen.structure.StructureSet> holder : structureState.possibleStructureSets()) {
            net.minecraft.world.level.levelgen.structure.placement.StructurePlacement placement = holder.value().placement();
            java.util.List<net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry> list = holder.value().structures();

            boolean alreadyHasValidStart = false;
            for (net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry entry : list) {
                net.minecraft.world.level.levelgen.structure.StructureStart start =
                    structureManager.getStartForStructure(sectionPos, entry.structure().value(), chunk);
                if (start != null && start.isValid()) { alreadyHasValidStart = true; break; }
            }
            if (alreadyHasValidStart) continue;

            if (!placement.isStructureChunk(structureState, chunkPos.x, chunkPos.z)) continue;

            // 오라클은 possibleStructureSets를 desert_pyramids 단일 set(단일 entry)으로 제한해두었으므로
            // vanilla의 가중치-다중 entry 분기(weight 기반 랜덤 선택)는 이 하네스에서 도달 불가.
            if (list.size() != 1) {
                throw new IllegalStateException("oracle harness only mirrors single-entry structure sets: " + list.size());
            }
            tryGenerateStructureNoBukkitEvent(
                list.get(0), structureManager, registryAccess, randomState, templateManager,
                structureState.getLevelSeed(), chunk, chunkPos, sectionPos, generator);
        }
    }

    private static boolean tryGenerateStructureNoBukkitEvent(
        net.minecraft.world.level.levelgen.structure.StructureSet.StructureSelectionEntry weightedEntry,
        net.minecraft.world.level.StructureManager structureManager,
        RegistryAccess registryAccess,
        RandomState randomState,
        net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager templateManager,
        long seed,
        ProtoChunk chunk,
        ChunkPos chunkPos,
        SectionPos sectionPos,
        NoiseBasedChunkGenerator generator
    ) {
        net.minecraft.world.level.levelgen.structure.Structure structure = weightedEntry.structure().value();
        net.minecraft.world.level.levelgen.structure.StructureStart existingStart =
            structureManager.getStartForStructure(sectionPos, structure, chunk);
        int references = existingStart != null ? existingStart.getReferences() : 0;
        net.minecraft.core.HolderSet<Biome> biomes = structure.biomes();
        java.util.function.Predicate<Holder<Biome>> validBiome = biomes::contains;
        net.minecraft.world.level.levelgen.structure.StructureStart structureStart = structure.generate(
            registryAccess, generator, generator.getBiomeSource(), randomState, templateManager,
            seed, chunkPos, references, chunk, validBiome);
        if (structureStart.isValid()) {
            structureManager.setStartForStructure(sectionPos, structure, structureStart, chunk);
            return true;
        }
        return false;
    }

    /**
     * vanilla(+Paper) StructureStart.placeInChunk 미러 — Paper의 CraftStructureTransformer가
     * AsyncStructureGenerateEvent용 structuremanager.level.getMinecraftWorld().getWorld()를 무조건
     * 호출해 헤드리스 오라클에서 NPE(위 createStructuresNoBukkitEvent와 동일 근본 원인)가 난다.
     * 리스너 없는 두 번째 CraftStructureTransformer 생성자(빈 transformer 목록)로 이벤트 발사를 생략한다.
     * afterPlace(suspicious sand/collapsed roof 배치, 블록에 실제 영향)는 그대로 호출한다.
     */
    private static void placeInChunkNoBukkitEvent(
        net.minecraft.world.level.levelgen.structure.StructureStart start,
        OracleWorldGenLevel level,
        net.minecraft.world.level.StructureManager structureManager,
        NoiseBasedChunkGenerator generator,
        net.minecraft.world.level.levelgen.WorldgenRandom random,
        net.minecraft.world.level.levelgen.structure.BoundingBox chunkBox,
        ChunkPos chunkPos
    ) {
        java.util.List<net.minecraft.world.level.levelgen.structure.StructurePiece> pieces = start.getPieces();
        if (pieces.isEmpty()) return;

        net.minecraft.world.level.levelgen.structure.BoundingBox firstBox =
            pieces.get(0).getBoundingBox();
        net.minecraft.core.BlockPos center = firstBox.getCenter();
        net.minecraft.core.BlockPos boundsCenter = new net.minecraft.core.BlockPos(center.getX(), firstBox.minY(), center.getZ());

        // Paper의 CraftStructureTransformer 래퍼(AsyncStructureGenerateEvent + CraftLimitedRegion)는 둘 다
        // access.getMinecraftWorld().getWorld()를 무조건 요구해 헤드리스 오라클에서 NPE — 플러그인 리스너가
        // 없으면 이 래퍼는 블록 출력에 아무 영향이 없으므로, vanilla 원본 로직(Paper 패치가 주석 처리한
        // "CraftBukkit start/end" 블록의 vanilla 부분)대로 piece.postProcess를 level에 직접 호출한다.
        java.util.List<net.minecraft.world.level.levelgen.structure.StructurePiece> intersecting =
            pieces.stream().filter(p -> p.getBoundingBox().intersects(chunkBox)).toList();
        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : intersecting) {
            piece.postProcess(level, structureManager, generator, random, chunkBox, chunkPos, boundsCenter);
        }

        start.getStructure().afterPlace(level, structureManager, generator, random, chunkBox, chunkPos,
            new net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer(pieces));
    }

    private static BlockState[] snapshotChunk(ProtoChunk chunk, int cx, int cz) {
        BlockState[] states = new BlockState[FixtureFormat.HEIGHT * 16 * 16];
        int idx = 0;
        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                for (int localX = 0; localX < 16; localX++) {
                    states[idx++] = chunk.getBlockState(new net.minecraft.core.BlockPos(cx * 16 + localX, y, cz * 16 + localZ));
                }
            }
        }
        return states;
    }

    private static ProtoChunk buildCarvedProtoChunk(
        long seed, int cx, int cz,
        NoiseBasedChunkGenerator generator,
        RandomState randomState,
        NoiseGeneratorSettings settings,
        Aquifer.FluidPicker fluidPicker,
        Registry<Biome> biomeRegistry,
        MultiNoiseBiomeSource biomeSource,
        RegistryAccess registryAccess
    ) throws Exception {
        ChunkPos chunkPos = new ChunkPos(cx, cz);
        LevelHeightAccessor heightAccessor = LevelHeightAccessor.create(FixtureFormat.MIN_Y, FixtureFormat.HEIGHT);
        ProtoChunk chunk = new ProtoChunk(chunkPos, UpgradeData.EMPTY, heightAccessor, biomeRegistry, null);

        Beardifier emptyBeardifier = new Beardifier(
            new ObjectArrayList<Beardifier.Rigid>().iterator(),
            new ObjectArrayList<JigsawJunction>().iterator()
        );
        chunk.getOrCreateNoiseChunk(ca ->
            NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty())
        );

        generator.createBiomes(randomState, Blender.empty(), null, chunk).join();
        generator.fillFromNoise(Blender.empty(), randomState, null, chunk).join();

        BiomeManager biomeManager = new BiomeManager(
            (x, y, z) -> biomeSource.getNoiseBiome(x, y, z, randomState.sampler()),
            BiomeManager.obfuscateSeed(seed)
        );
        WorldGenerationContext worldGenContext = new WorldGenerationContext(generator, heightAccessor);
        generator.buildSurface(chunk, worldGenContext, randomState, null, biomeManager, biomeRegistry, Blender.empty());

        NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk(ca ->
            NoiseChunk.forChunk(ca, randomState, emptyBeardifier, settings, fluidPicker, Blender.empty()));
        Aquifer aquifer = noiseChunk.aquifer();
        net.minecraft.world.level.levelgen.carver.CarvingContext carvingCtx =
            new net.minecraft.world.level.levelgen.carver.CarvingContext(
                generator, registryAccess, heightAccessor,
                noiseChunk, randomState, settings.surfaceRule(), null);

        var carverReg = registryAccess.lookupOrThrow(Registries.CONFIGURED_CARVER);
        var caveCarver = carverReg.getOrThrow(ResourceKey.create(
            Registries.CONFIGURED_CARVER, net.minecraft.resources.ResourceLocation.parse("minecraft:cave")));
        var caveExtraCarver = carverReg.getOrThrow(ResourceKey.create(
            Registries.CONFIGURED_CARVER, net.minecraft.resources.ResourceLocation.parse("minecraft:cave_extra_underground")));
        var canyonCarver = carverReg.getOrThrow(ResourceKey.create(
            Registries.CONFIGURED_CARVER, net.minecraft.resources.ResourceLocation.parse("minecraft:canyon")));
        java.util.List<ConfiguredWorldCarver<?>> carvers =
            java.util.List.of(caveCarver.value(), caveExtraCarver.value(), canyonCarver.value());

        net.minecraft.world.level.chunk.CarvingMask carvingMask =
            chunk.getOrCreateCarvingMask(GenerationStep.Carving.AIR);
        WorldgenRandom worldgenRandom = new WorldgenRandom(
            new net.minecraft.world.level.levelgen.LegacyRandomSource(0L));
        java.util.function.Function<net.minecraft.core.BlockPos, Holder<Biome>> biomeAccessor =
            pos -> biomeSource.getNoiseBiome(pos.getX() >> 2, 0, pos.getZ() >> 2, randomState.sampler());

        for (int originX = cx - 8; originX <= cx + 8; originX++) {
            for (int originZ = cz - 8; originZ <= cz + 8; originZ++) {
                ChunkPos originPos = new ChunkPos(originX, originZ);
                int carverIndex = 0;
                for (ConfiguredWorldCarver<?> carver : carvers) {
                    worldgenRandom.setLargeFeatureSeed(seed + carverIndex, originX, originZ);
                    if (carver.isStartChunk(worldgenRandom)) {
                        carver.carve(carvingCtx, chunk, biomeAccessor, worldgenRandom, aquifer, originPos, carvingMask);
                    }
                    carverIndex++;
                }
            }
        }

        // round11: 실서버는 carve 완료 시 chunk.setStatus(CARVERS)로 진행 상태를 올린다.
        // ProtoChunk.setBlockState는 getPersistedStatus().heightmapsAfter()에 포함된 타입만 라이브
        // 갱신하는데, EMPTY 상태(기본값, 이전까지 이 오라클이 한 번도 setPersistedStatus를 호출하지
        // 않았음)의 heightmapsAfter는 WORLDGEN_HEIGHTMAPS(OCEAN_FLOOR_WG·WORLD_SURFACE_WG)뿐이라
        // OCEAN_FLOOR(라이브 타입, birch_tall 등 HeightmapPlacement가 실제 참조)는 decorate 중 setBlock으로
        // 전혀 갱신되지 않고 carve 직후 최초 프라임 값에 그대로 고정됐었다(브리프11 (b) 판별로 실측 확인:
        // ORACLE_LEAF_WRITE 추적 시 여러 candidate가 같은 컬럼에 leaf/log를 써도 oceanFloorAfter가 87로
        // 불변). CARVERS/FEATURES 모두 FINAL_HEIGHTMAPS(OCEAN_FLOOR 포함)를 쓰므로 여기서 상태를 올려야
        // 실서버와 동일하게 decorate 중 OCEAN_FLOOR가 매 setBlock마다 라이브 갱신된다.
        chunk.setPersistedStatus(net.minecraft.world.level.chunk.status.ChunkStatus.CARVERS);

        return chunk;
    }

    @SuppressWarnings("unchecked")
    private static void placeVanillaFeatures(
        OracleWorldGenLevel level,
        ProtoChunk center,
        NoiseBasedChunkGenerator generator,
        MultiNoiseBiomeSource biomeSource,
        RegistryAccess registryAccess
    ) throws Exception {
        placeVanillaFeatures(level, center, generator, biomeSource, registryAccess, null, null, null);
    }

    private static java.util.Map<Integer, java.util.List<net.minecraft.world.level.levelgen.structure.Structure>>
            STRUCTURES_BY_STEP;

    /**
     * vanilla applyBiomeDecoration line ~321 미러:
     * registry.stream().collect(groupingBy(structure.step().ordinal())) — registry stream 순서 유지.
     * 각 step 그룹 내 순번이 setFeatureSeed의 구조물 인덱스 i1이 된다.
     */
    private static java.util.Map<Integer, java.util.List<net.minecraft.world.level.levelgen.structure.Structure>>
            structuresByStep(HolderLookup.Provider registries) {
        if (STRUCTURES_BY_STEP == null) {
            java.util.Map<Integer, java.util.List<net.minecraft.world.level.levelgen.structure.Structure>> map =
                new java.util.LinkedHashMap<>();
            java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, String> names =
                new java.util.IdentityHashMap<>();
            registries.lookupOrThrow(Registries.STRUCTURE).listElements().forEach(ref -> {
                net.minecraft.world.level.levelgen.structure.Structure s = ref.value();
                map.computeIfAbsent(s.step().ordinal(), k -> new java.util.ArrayList<>()).add(s);
                names.put(s, ref.key().location().toString());
            });
            for (var e : map.entrySet()) {
                for (int i = 0; i < e.getValue().size(); i++) {
                    String id = names.get(e.getValue().get(i));
                    if ("minecraft:desert_pyramid".equals(id)) {
                        System.out.printf("ORACLE structure index: step=%d i1=%d id=%s (group size=%d)%n",
                            e.getKey(), i, id, e.getValue().size());
                        for (int j = 0; j < e.getValue().size(); j++) {
                            System.out.printf("ORACLE step=%d group[%d]=%s%n",
                                e.getKey(), j, names.get(e.getValue().get(j)));
                        }
                    }
                }
            }
            STRUCTURES_BY_STEP = map;
        }
        return STRUCTURES_BY_STEP;
    }

    /**
     * featurestruct 전용 오버로드 — structureManager!=null이면 vanilla ChunkGenerator.applyBiomeDecoration의
     * 실제 로직(구조물 placeInChunk가 매 step의 feature 배치보다 먼저)을 직접 미러링한다.
     * generator.applyBiomeDecoration을 직접 호출하지 않는 이유: Paper 서버 addVanillaDecorations가
     * WorldGenLevel.getMinecraftWorld().paperConfig()(Paper의 feature-seed override 설정)를 참조하는데
     * 이 헤드리스 오라클엔 실 ServerLevel이 없어 NPE — 어차피 기본 설정에서는 override map이 비어 있어
     * (getLong==-1L) 항상 no-op이므로, 그 경로를 우회하고 vanilla 핵심 로직만 손으로 재현한다.
     * (desert_pyramid만 지원 — Furnace가 구현한 유일한 구조물, S4+ 범위 외 구조물은 대상 아님)
     */
    @SuppressWarnings("unchecked")
    private static void placeVanillaFeatures(
        OracleWorldGenLevel level,
        ProtoChunk center,
        NoiseBasedChunkGenerator generator,
        MultiNoiseBiomeSource biomeSource,
        RegistryAccess registryAccess,
        net.minecraft.world.level.StructureManager structureManager,
        HolderLookup.Provider registries,
        net.minecraft.world.level.levelgen.structure.Structure canonicalPyramid
    ) throws Exception {
        ChunkPos chunkPos = center.getPos();
        SectionPos sectionPos = SectionPos.of(chunkPos, level.getMinSection());
        net.minecraft.core.BlockPos origin = sectionPos.origin();

        java.lang.reflect.Field featuresField = net.minecraft.world.level.chunk.ChunkGenerator.class.getDeclaredField("featuresPerStep");
        featuresField.setAccessible(true);
        java.util.function.Supplier<java.util.List<FeatureSorter.StepFeatureData>> supplier =
            (java.util.function.Supplier<java.util.List<FeatureSorter.StepFeatureData>>) featuresField.get(generator);
        java.util.List<FeatureSorter.StepFeatureData> steps = supplier.get();

        it.unimi.dsi.fastutil.objects.ObjectArraySet<Holder<Biome>> biomes =
            new it.unimi.dsi.fastutil.objects.ObjectArraySet<>();
        ChunkPos.rangeClosed(sectionPos.chunk(), 1).forEach(pos -> {
            ChunkAccess chunk = level.getChunk(pos.x, pos.z);
            for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections()) {
                section.getBiomes().getAll(biomes::add);
            }
        });
        biomes.retainAll(biomeSource.possibleBiomes());

        boolean debug = Boolean.getBoolean("oracle.featureDebug");
        boolean blockDebug = Boolean.getBoolean("oracle.featureBlockDebug");
        boolean rngTraceForChunk = ORACLE_RNG_TRACE.matchesChunk(chunkPos.x, chunkPos.z);
        if (debug) {
            java.util.TreeSet<String> biomeNames = new java.util.TreeSet<>();
            biomes.forEach(b -> biomeNames.add(b.unwrapKey().get().location().toString()));
            System.out.printf("ORACLE decorate cx=%d cz=%d biomes=%s%n",
                chunkPos.x, chunkPos.z, biomeNames);
        }

        WorldgenRandom random = rngTraceForChunk
            ? new TraceWorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                    net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()))
            : new WorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                    net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        long decorationSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());

        int stepCount = Math.max(GenerationStep.Decoration.values().length, steps.size());
        for (int step = 0; step < stepCount; step++) {
            if (structureManager != null && structureManager.shouldGenerateStructures()) {
                // vanilla ChunkGenerator.applyBiomeDecoration 미러 (F1):
                // 전체 Structure registry stream을 step ordinal로 groupingBy(registry 순서 유지),
                // step 그룹의 모든 구조물에 대해 setFeatureSeed(l, i1, step) 재시드 후 i1++ —
                // start가 없는 구조물도 i1을 소비한다. 인덱스 하드코딩 금지(desert_pyramid ≠ 0).
                java.util.List<net.minecraft.world.level.levelgen.structure.Structure> stepStructures =
                    structuresByStep(registries).getOrDefault(step, java.util.List.of());
                net.minecraft.world.level.levelgen.structure.BoundingBox writableArea =
                    new net.minecraft.world.level.levelgen.structure.BoundingBox(
                        chunkPos.getMinBlockX(), level.getMinBuildHeight() + 1, chunkPos.getMinBlockZ(),
                        chunkPos.getMinBlockX() + 15, level.getMaxBuildHeight() - 1, chunkPos.getMinBlockZ() + 15);
                int i1 = 0;
                for (net.minecraft.world.level.levelgen.structure.Structure structure : stepStructures) {
                    // d3: VanillaRegistries.createLookup().listElements() 원순서는 JVM 실행마다 요동함이
                    // 실측 확인됨(structure_order.txt, 5회 중 4회 diff — i1이 3~24 사이로 관측). d3에서 OracleMain의
                    // copyRegistry/biomeRegistry/carverRegistry 빌드를 ResourceLocation 사전순 정렬로 핀 고정했으나
                    // (sortedElements 메서드), 이 structuresByStep()은 별도의 원본 registries(HolderLookup.Provider,
                    // 정렬 미적용) 인스턴스를 직접 참조하므로 위 핀과 무관하게 여전히 요동한다. 블록에 영향 주는 건
                    // start가 있는 구조물(현재 desert_pyramid)의 자기 i1뿐이므로, FeaturePlacer.DESERT_PYRAMID_STRUCTURE_INDEX와
                    // 동일한 값(사전순 STEP 4 그룹 내 순번 = 1, structure_order.txt 실측)으로 핀 고정해
                    // 픽스처 재생성을 재현 가능하게 만든다. 진짜 실서버 순서 검증은 .mca 자가검증 게이트(스펙 §3) 몫.
                    // instanceof 판별 — vanilla에서 DesertPyramidStructure 인스턴스는 desert_pyramid 하나뿐.
                    // (listElements() 재호출 기반 identity 비교는 datagen lookup이 holder를 재생성해 실패한다)
                    boolean isDesertPyramid = structure instanceof
                        net.minecraft.world.level.levelgen.structure.structures.DesertPyramidStructure;
                    // startsForStructure는 Structure identity 키 조회 — structuresByStep의 인스턴스가
                    // createStructures가 쓴 인스턴스(structure set 경유)와 다를 수 있어 canonical로 조회한다.
                    net.minecraft.world.level.levelgen.structure.Structure queryStructure =
                        isDesertPyramid ? canonicalPyramid : structure;
                    int effectiveI1 = isDesertPyramid ? 1 : i1;
                    random.setFeatureSeed(decorationSeed, effectiveI1, step);
                    if (isDesertPyramid) { // TEMP S3 debug — remove before ship
                        System.out.printf("ORACLE_STARTS center count=%d%n",
                            structureManager.startsForStructure(sectionPos, queryStructure).size());
                        int peek = random.nextInt(3);
                        for (net.minecraft.world.level.levelgen.structure.StructureStart start :
                                structureManager.startsForStructure(sectionPos, queryStructure)) {
                            for (net.minecraft.world.level.levelgen.structure.StructurePiece p : start.getPieces()) {
                                var bb = p.getBoundingBox();
                                int min = Integer.MAX_VALUE;
                                for (int z = bb.minZ(); z <= bb.maxZ(); z++) {
                                    for (int x = bb.minX(); x <= bb.maxX(); x++) {
                                        min = Math.min(min, level.getHeightmapPos(
                                            net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                            new net.minecraft.core.BlockPos(x, 0, z)).getY());
                                    }
                                }
                                System.out.printf("ORACLE_HEIGHT min=%d heightArgPeek=%d bbox=[%d..%d]x[%d..%d]%n",
                                    min, -peek, bb.minX(), bb.maxX(), bb.minZ(), bb.maxZ());
                            }
                        }
                        random.setFeatureSeed(decorationSeed, effectiveI1, step);
                    }
                    for (net.minecraft.world.level.levelgen.structure.StructureStart start :
                            structureManager.startsForStructure(sectionPos, queryStructure)) {
                        placeInChunkNoBukkitEvent(start, level, structureManager, generator, random, writableArea, chunkPos);
                    }
                    i1++;
                }
            }
            if (step >= steps.size()) {
                continue;
            }
            it.unimi.dsi.fastutil.ints.IntArraySet featureIndexes =
                new it.unimi.dsi.fastutil.ints.IntArraySet();
            for (Holder<Biome> biome : biomes) {
                java.util.List<HolderSet<PlacedFeature>> biomeFeatures =
                    biome.value().getGenerationSettings().features();
                if (step < biomeFeatures.size()) {
                    HolderSet<PlacedFeature> holderSet = biomeFeatures.get(step);
                    FeatureSorter.StepFeatureData stepData = steps.get(step);
                    holderSet.stream().map(Holder::value)
                        .forEach(feature -> featureIndexes.add(stepData.indexMapping().applyAsInt(feature)));
                }
            }

            int[] sorted = featureIndexes.toIntArray();
            java.util.Arrays.sort(sorted);
            if (debug && sorted.length > 0) {
                System.out.printf("ORACLE step=%d indices=%s%n", step, java.util.Arrays.toString(sorted));
            }
            FeatureSorter.StepFeatureData stepData = steps.get(step);
            for (int featureIndex : sorted) {
                PlacedFeature feature = stepData.features().get(featureIndex);
                String featureId = feature.feature().unwrapKey().map(k -> k.location().toString()).orElse("?");
                long featureSeed = decorationSeed + featureIndex + 10000L * step;
                random.setFeatureSeed(decorationSeed, featureIndex, step);
                BlockState[] before = blockDebug ? snapshotGrid(level, chunkPos) : null;
                if (debug) {
                    System.out.printf("ORACLE step=%d idx=%d featureSeed=%d id=%s%n",
                        step, featureIndex, featureSeed, featureId);
                }
                boolean trace = ORACLE_RNG_TRACE.shouldTrace(chunkPos.x, chunkPos.z, step, featureIndex, featureId);
                if (trace) ORACLE_RNG_TRACE.begin(level.getSeed(), chunkPos.x, chunkPos.z, step, featureIndex, featureId, featureSeed);
                if (trace && "minecraft:birch_tall".equals(featureId)) {
                    logOracleTreePlacements(feature, level, generator, decorationSeed, featureIndex, step, origin);
                }
                boolean placed;
                try {
                    placed = feature.placeWithBiomeCheck(level, generator, random, origin);
                } finally {
                    if (trace) ORACLE_RNG_TRACE.end();
                }
                if (blockDebug) {
                    BlockState[] after = snapshotGrid(level, chunkPos);
                    int changed = countChanged(before, after);
                    if (placed || changed > 0) {
                        System.out.printf(
                            "  ORACLE step=%d idx=%d id=%s placed=%s changed=%d firstChange=%s%n",
                            step, featureIndex, featureId, placed, changed, firstChange(before, after, chunkPos));
                        logChangedBlocks(before, after, chunkPos, step, featureIndex, featureId);
                    }
                }
            }
        }
        level.setCurrentlyGenerating(null);
    }

    private static void logOracleTreePlacements(
        PlacedFeature feature,
        WorldGenLevel level,
        NoiseBasedChunkGenerator generator,
        long decorationSeed,
        int featureIndex,
        int step,
        net.minecraft.core.BlockPos origin
    ) {
        WorldgenRandom replayRandom = new WorldgenRandom(
            new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        replayRandom.setFeatureSeed(decorationSeed, featureIndex, step);
        PlacementContext context = new PlacementContext(level, generator, java.util.Optional.of(feature));
        int[] tree = {0};
        java.util.stream.Stream<net.minecraft.core.BlockPos> positions = java.util.stream.Stream.of(origin);
        for (Object modifier : feature.placement()) {
            Method method = resolvePlacementMethod(modifier);
            positions = positions.flatMap(pos -> invokePlacementModifier(method, modifier, context, replayRandom, pos));
        }
        positions.forEach(pos -> System.out.printf(
            "ORACLE_TREE_PLACE origin=(%d,%d,%d) tree=%d%n",
            pos.getX(), pos.getY(), pos.getZ(), ++tree[0]));
    }

    private static Method resolvePlacementMethod(Object modifier) {
        for (Method method : modifier.getClass().getMethods()) {
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 3) continue;
            if (!java.util.stream.Stream.class.isAssignableFrom(method.getReturnType())) continue;
            if (!params[0].getSimpleName().equals("PlacementContext")) continue;
            if (!params[2].getSimpleName().equals("BlockPos")) continue;
            method.setAccessible(true);
            return method;
        }
        for (Method method : modifier.getClass().getMethods()) {
            System.out.printf("ORACLE_MODIFIER_METHOD class=%s method=%s%n", modifier.getClass().getName(), method);
        }
        throw new IllegalStateException("No placement stream method for " + modifier.getClass());
    }

    @SuppressWarnings("unchecked")
    private static java.util.stream.Stream<net.minecraft.core.BlockPos> invokePlacementModifier(
        Method method,
        Object modifier,
        PlacementContext context,
        WorldgenRandom replayRandom,
        net.minecraft.core.BlockPos pos
    ) {
        try {
            return (java.util.stream.Stream<net.minecraft.core.BlockPos>) method.invoke(modifier, context, replayRandom, pos);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke placement modifier " + modifier.getClass(), e);
        }
    }

    /** origin 청크(chunkPos)의 3x3 grid 전체(48x48)를 level(=OracleWorldGenLevel, 이웃 청크로 dispatch됨)을 통해
     * 스냅샷 — cross-origin spill(이웃 청크로 유출되는 나무 캐노피) 탐지용. ProtoChunk.getBlockState는 자기
     * 청크 밖을 지원하지 않으므로 level.getBlockState를 사용(Furnace 쪽 snapshotGrid와 대응). */
    private static BlockState[] snapshotGrid(WorldGenLevel level, ChunkPos chunkPos) {
        int minX = chunkPos.getMinBlockX() - 16;
        int minZ = chunkPos.getMinBlockZ() - 16;
        BlockState[] snapshot = new BlockState[FixtureFormat.HEIGHT * 48 * 48];
        int index = 0;
        for (int y = FixtureFormat.MIN_Y; y < FixtureFormat.MIN_Y + FixtureFormat.HEIGHT; y++) {
            for (int lz = 0; lz < 48; lz++) {
                for (int lx = 0; lx < 48; lx++) {
                    snapshot[index++] = level.getBlockState(new net.minecraft.core.BlockPos(minX + lx, y, minZ + lz));
                }
            }
        }
        return snapshot;
    }

    private static int countChanged(BlockState[] before, BlockState[] after) {
        int changed = 0;
        for (int i = 0; i < before.length; i++) {
            if (before[i] != after[i]) changed++;
        }
        return changed;
    }

    private static String firstChange(BlockState[] before, BlockState[] after, ChunkPos chunkPos) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 48 * 48;
            int yIndex = i / plane;
            int rem = i % plane;
            int lz = rem / 48 - 16;
            int lx = rem % 48 - 16;
            int y = FixtureFormat.MIN_Y + yIndex;
            return String.format("originCx=%d originCz=%d gridLx=%d y=%d gridLz=%d %s->%s",
                chunkPos.x, chunkPos.z, lx, y, lz, before[i], after[i]);
        }
        return "-";
    }

    private static void logChangedBlocks(BlockState[] before, BlockState[] after, ChunkPos chunkPos, int step, int idx, String featureId) {
        for (int i = 0; i < before.length; i++) {
            if (before[i] == after[i]) continue;
            int plane = 48 * 48;
            int yIndex = i / plane;
            int rem = i % plane;
            int lz = rem / 48 - 16;
            int lx = rem % 48 - 16;
            int y = FixtureFormat.MIN_Y + yIndex;
            System.out.printf(
                "BLOCK_DELTA source=ORACLE originCx=%d originCz=%d step=%d idx=%d id=%s wx=%d y=%d wz=%d old=%s new=%s%n",
                chunkPos.x, chunkPos.z, step, idx, featureId, chunkPos.getMinBlockX() + lx, y, chunkPos.getMinBlockZ() + lz,
                before[i], after[i]);
        }
    }

    private static void dumpXoroshiroCheck() {
        net.minecraft.world.level.levelgen.XoroshiroRandomSource src =
            new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed());
        src.setSeed(1234L);
        System.out.print("xoro setSeed(1234) nextLong x5: ");
        for (int i = 0; i < 5; i++) System.out.print(src.nextLong() + " ");
        System.out.println();

        src.setSeed(1234L);
        System.out.print("xoro setSeed(1234) nextInt(16) x5: ");
        for (int i = 0; i < 5; i++) System.out.print(src.nextInt(16) + " ");
        System.out.println();

        net.minecraft.world.level.levelgen.WorldgenRandom wr =
            new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                    net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        long ds = wr.setDecorationSeed(1234L, 0, 0);
        System.out.println("decorationSeed(1234,0,0): " + ds);
        wr.setFeatureSeed(ds, 0, 0);
        System.out.print("after setFeatureSeed(ds,0,0) nextInt(16) x5: ");
        for (int i = 0; i < 5; i++) System.out.print(wr.nextInt(16) + " ");
        System.out.println();

        net.minecraft.world.level.levelgen.WorldgenRandom wr2 =
            new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                    net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        wr2.setSeed(1234L);
        System.out.print("WGR setSeed(1234) nextInt() x5: ");
        for (int i = 0; i < 5; i++) System.out.print(wr2.nextInt() + " ");
        System.out.println();

        wr2.setSeed(1234L);
        System.out.print("WGR setSeed(1234) nextLong() x5: ");
        for (int i = 0; i < 5; i++) System.out.print(wr2.nextLong() + " ");
        System.out.println();

        wr2.setSeed(1234L);
        System.out.print("WGR setSeed(1234) nextDouble() x5: ");
        for (int i = 0; i < 5; i++) System.out.print(wr2.nextDouble() + " ");
        System.out.println();

        wr2.setSeed(1234L);
        System.out.print("WGR setSeed(1234) nextFloat() x5: ");
        for (int i = 0; i < 5; i++) System.out.print(wr2.nextFloat() + " ");
        System.out.println();

        wr2.setSeed(1234L);
        System.out.print("WGR setSeed(1234) nextBoolean() x5: ");
        for (int i = 0; i < 5; i++) System.out.print(wr2.nextBoolean() + " ");
        System.out.println();

        // featureSeed for cx=7,cz=-2 step=6 idx=10 (ore_coal_lower)
        long featureSeed7m2 = 1031541745140545356L;
        net.minecraft.world.level.levelgen.WorldgenRandom wr3 =
            new net.minecraft.world.level.levelgen.WorldgenRandom(
                new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                    net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
        wr3.setSeed(featureSeed7m2);
        System.out.print("WGR setSeed(featureSeed_7m2) nextFloat nextInt(3) nextInt(3) nextInt(16) nextInt(16) nextInt(97) nextInt(97): ");
        System.out.print(wr3.nextFloat() + " ");
        System.out.print(wr3.nextInt(3) + " ");
        System.out.print(wr3.nextInt(3) + " ");
        System.out.print(wr3.nextInt(16) + " ");
        System.out.print(wr3.nextInt(16) + " ");
        System.out.print(wr3.nextInt(97) + " ");
        System.out.print(wr3.nextInt(97));
        System.out.println();
    }

    private static void dumpBiomeChunk(
        long seed, int cx, int cz,
        MultiNoiseBiomeSource biomeSource,
        RandomState randomState,
        Path outDir
    ) throws Exception {
        Climate.Sampler sampler = randomState.sampler();

        String[] biomeNames = new String[1536];
        for (int qy = 0; qy < 96; qy++) {
            int vanillaQY = qy - 16;
            for (int qz = 0; qz < 4; qz++) {
                for (int qx = 0; qx < 4; qx++) {
                    int worldQX = cx * 4 + qx;
                    int worldQZ = cz * 4 + qz;
                    Holder<Biome> holder = biomeSource.getNoiseBiome(worldQX, vanillaQY, worldQZ, sampler);
                    biomeNames[qy * 16 + qz * 4 + qx] =
                        holder.unwrapKey().orElseThrow().location().toString();
                }
            }
        }

        // Calculate body size for ByteBuffer allocation
        byte[][] nameBytes = new byte[1536][];
        int bodySize = 0;
        for (int i = 0; i < 1536; i++) {
            nameBytes[i] = biomeNames[i].getBytes(java.nio.charset.StandardCharsets.UTF_8);
            bodySize += 2 + nameBytes[i].length;
        }

        // Header: int32 magic + int64 seed + int32 cx + int32 cz = 4+8+4+4 = 20 bytes
        ByteBuffer buf = ByteBuffer.allocate(20 + bodySize)
            .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(0x42494F4D);  // "BIOM" magic
        buf.putLong(seed);
        buf.putInt(cx);
        buf.putInt(cz);
        for (byte[] nb : nameBytes) {
            buf.putShort((short) nb.length);
            buf.put(nb);
        }

        Path out = outDir.resolve("biome_" + seed + "_" + cx + "_" + cz + ".bin");
        try (FileOutputStream fos = new FileOutputStream(out.toFile())) {
            fos.write(buf.array());
        }
        System.out.printf("Wrote %s (%d bytes, 1536 quarts)%n", out, buf.capacity());
    }

    private static void dumpBiomeBlock(
        long seed, int x, int y, int z,
        MultiNoiseBiomeSource biomeSource,
        RandomState randomState
    ) {
        BiomeManager biomeManager = new BiomeManager(
            (qx, qy, qz) -> biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler()),
            BiomeManager.obfuscateSeed(seed)
        );
        Holder<Biome> managerBiome = biomeManager.getBiome(new net.minecraft.core.BlockPos(x, y, z));
        int qx = net.minecraft.core.QuartPos.fromBlock(x);
        int qy = net.minecraft.core.QuartPos.fromBlock(y);
        int qz = net.minecraft.core.QuartPos.fromBlock(z);
        Holder<Biome> rawBiome = biomeSource.getNoiseBiome(qx, qy, qz, randomState.sampler());
        System.out.printf("BIOMEBLOCK seed=%d block=(%d,%d,%d) rawQuart=(%d,%d,%d) raw=%s manager=%s%n",
            seed, x, y, z, qx, qy, qz,
            rawBiome.unwrapKey().orElseThrow().location(),
            managerBiome.unwrapKey().orElseThrow().location());
    }

    /**
     * Dumps all block default state IDs from vanilla BLOCK_STATE_REGISTRY.
     * Format: "# states <total>\n" + one line per block: "minecraft:name defaultStateId\n"
     * Overwrites furnace-server/src/main/resources/blocks/1.21.1.txt
     */
    private static void dumpBlocks(Path outDir) throws Exception {
        Files.createDirectories(outDir);
        int totalStates = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY.size();
        StringBuilder sb = new StringBuilder();
        sb.append("# states ").append(totalStates).append("\n");
        // Iterate all registered blocks and get their default state ID
        for (net.minecraft.world.level.block.Block block :
                net.minecraft.core.registries.BuiltInRegistries.BLOCK) {
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                .getKey(block).toString();
            int defaultId = net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY
                .getId(block.defaultBlockState());
            sb.append(name).append(" ").append(defaultId).append("\n");
        }
        Path out = outDir.resolve("1.21.1.txt");
        Files.writeString(out, sb.toString());
        System.out.println("Wrote " + out + " (" + totalStates + " total states)");
    }

    private static byte classify(BlockState state) {
        if (state.isAir()) return FixtureFormat.AIR;
        if (state.is(Blocks.STONE)) return FixtureFormat.STONE;
        net.minecraft.world.level.material.FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            if (fluid.getType() == net.minecraft.world.level.material.Fluids.WATER) return FixtureFormat.WATER;
            if (fluid.getType() == net.minecraft.world.level.material.Fluids.LAVA) return FixtureFormat.LAVA;
        }
        return FixtureFormat.OTHER;
    }

    private static final class OracleRngTrace {
        private final boolean enabled = Boolean.getBoolean("oracle.rngTrace");
        private final int targetCx = Integer.getInteger("oracle.rngTraceCx", Integer.MIN_VALUE);
        private final int targetCz = Integer.getInteger("oracle.rngTraceCz", Integer.MIN_VALUE);
        private final int targetStep = Integer.getInteger("oracle.rngTraceStep", Integer.MIN_VALUE);
        private final int targetIdx = Integer.getInteger("oracle.rngTraceIdx", Integer.MIN_VALUE);
        private final String targetId = System.getProperty("oracle.rngTraceId");
        private final ThreadLocal<Integer> calls = new ThreadLocal<>();

        boolean matchesChunk(int cx, int cz) {
            if (!enabled) return false;
            if (targetCx != Integer.MIN_VALUE && targetCx != cx) return false;
            return targetCz == Integer.MIN_VALUE || targetCz == cz;
        }

        boolean shouldTrace(int cx, int cz, int step, int idx, String id) {
            if (!matchesChunk(cx, cz)) return false;
            if (targetStep != Integer.MIN_VALUE && targetStep != step) return false;
            if (targetIdx != Integer.MIN_VALUE && targetIdx != idx) return false;
            return targetId == null || targetId.equals(id);
        }

        void begin(long seed, int cx, int cz, int step, int idx, String id, long featureSeed) {
            calls.set(0);
            System.out.printf(
                "ORACLE_RNG_BEGIN seed=%d cx=%d cz=%d step=%d idx=%d id=%s featureSeed=%d%n",
                seed, cx, cz, step, idx, id, featureSeed);
        }

        void end() {
            Integer value = calls.get();
            if (value != null) {
                System.out.printf("ORACLE_RNG_END calls=%d%n", value);
            }
            calls.remove();
        }

        void nextInt(int value) {
            Integer call = calls.get();
            if (call == null) return;
            call++;
            calls.set(call);
            System.out.printf("ORACLE_RNG call=%d method=nextInt value=%d%n", call, value);
        }

        void nextInt(int bound, int value) {
            Integer call = calls.get();
            if (call == null) return;
            call++;
            calls.set(call);
            System.out.printf("ORACLE_RNG call=%d method=nextInt bound=%d value=%d%n", call, bound, value);
        }

        void nextFloat(float value) {
            Integer call = calls.get();
            if (call == null) return;
            call++;
            calls.set(call);
            System.out.printf("ORACLE_RNG call=%d method=nextFloat value=%s%n", call, Float.toString(value));
        }

        void nextDouble(double value) {
            Integer call = calls.get();
            if (call == null) return;
            call++;
            calls.set(call);
            System.out.printf("ORACLE_RNG call=%d method=nextDouble value=%s%n", call, Double.toString(value));
        }

        void nextBoolean(boolean value) {
            Integer call = calls.get();
            if (call == null) return;
            call++;
            calls.set(call);
            System.out.printf("ORACLE_RNG call=%d method=nextBoolean value=%s%n", call, Boolean.toString(value));
        }
    }

    private static final class TraceWorldgenRandom extends WorldgenRandom {
        private TraceWorldgenRandom(XoroshiroRandomSource randomSource) {
            super(randomSource);
        }

        @Override
        public int nextInt() {
            int value = super.nextInt();
            ORACLE_RNG_TRACE.nextInt(value);
            return value;
        }

        @Override
        public int nextInt(int bound) {
            int value = super.nextInt(bound);
            ORACLE_RNG_TRACE.nextInt(bound, value);
            return value;
        }

        @Override
        public float nextFloat() {
            float value = super.nextFloat();
            ORACLE_RNG_TRACE.nextFloat(value);
            return value;
        }

        @Override
        public double nextDouble() {
            double value = super.nextDouble();
            ORACLE_RNG_TRACE.nextDouble(value);
            return value;
        }

        @Override
        public boolean nextBoolean() {
            boolean value = super.nextBoolean();
            ORACLE_RNG_TRACE.nextBoolean(value);
            return value;
        }
    }
}
