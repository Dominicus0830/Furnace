// 바닐라 노이즈 지형 생성을 실행해 bin fixture를 덤프하는 오라클 진입점
package org.domi.oracle;

import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.doubles.DoubleArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.DetectedVersion;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
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

import java.util.IdentityHashMap;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class OracleMain {

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

            ((net.minecraft.core.MappedRegistry<net.minecraft.world.level.block.Block>)
                (Object) net.minecraft.core.registries.BuiltInRegistries.BLOCK).bindTags(tagMap);
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
        biomeLookup.listElements().forEach(ref ->
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
        carverLookup.listElements().forEach(ref ->
            carverRegistry.register(ref.key(), ref.value(), RegistrationInfo.BUILT_IN)
        );
        Registry<ConfiguredWorldCarver<?>> frozenCarverRegistry = carverRegistry.freeze();

        RegistryAccess registryAccess = new RegistryAccess.ImmutableRegistryAccess(
            java.util.List.of(frozenBiomeRegistry, frozenCarverRegistry));

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
            } else if (arg.equals("featureorder")) {
                dumpFeatureOrder(registries, biomeSource, outDir);
            } else if (arg.equals("blocks")) {
                Path blocksResDir = Paths.get("furnace-server", "src", "main", "resources", "blocks");
                dumpBlocks(blocksResDir);
            } else if (arg.equals("biomeinfonoise")) {
                dumpBiomeInfoNoise(outDir);
            } else {
                String[] parts = arg.split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState, aquiferSettingsHolder.value(), aquiferFluidPicker, frozenBiomeRegistry, outDir);
            }
        }

        System.out.println("Oracle done. Output: " + outDir.toAbsolutePath());
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
                for (var carver : carvers) {
                    worldgenRandom.setLargeFeatureSeed(seed, originX, originZ);
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
}
