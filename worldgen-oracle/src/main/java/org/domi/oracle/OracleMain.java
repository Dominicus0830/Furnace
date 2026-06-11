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
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.WorldGenerationContext;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

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

        // Chunk generator
        NoiseBasedChunkGenerator generator = new NoiseBasedChunkGenerator(biomeSource, settingsHolder);

        // Aquifer-enabled resources (real overworld settings)
        Holder<NoiseGeneratorSettings> aquiferSettingsHolder = noiseSettingsReg.getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        RandomState aquiferRandomState = RandomState.create(aquiferSettingsHolder.value(), noiseGetter, seed);
        NoiseBasedChunkGenerator aquiferGenerator = new NoiseBasedChunkGenerator(biomeSource, aquiferSettingsHolder);
        Aquifer.FluidStatus lavaStatusAq = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus seaStatusAq = new Aquifer.FluidStatus(aquiferSettingsHolder.value().seaLevel(), aquiferSettingsHolder.value().defaultFluid());
        Aquifer.FluidPicker aquiferFluidPicker = (x, y, z) -> y < Math.min(-54, aquiferSettingsHolder.value().seaLevel()) ? lavaStatusAq : seaStatusAq;

        // Fluid picker (mirrors NoiseBasedChunkGenerator.createFluidPicker)
        int seaLevel = noAquiferSettings.seaLevel();
        Aquifer.FluidStatus lavaStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        Aquifer.FluidStatus seaStatus = new Aquifer.FluidStatus(seaLevel, noAquiferSettings.defaultFluid());
        Aquifer.FluidPicker fluidPicker = (x, y, z) -> y < Math.min(-54, seaLevel) ? lavaStatus : seaStatus;

        // Build a real Registry<Biome> from the lookup, needed by ProtoChunk constructor.
        // HolderLookup.RegistryLookup is not a Registry, so we populate a MappedRegistry.
        HolderLookup.RegistryLookup<Biome> biomeLookup = registries.lookupOrThrow(Registries.BIOME);
        MappedRegistry<Biome> biomeRegistry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        biomeLookup.listElements().forEach(ref ->
            biomeRegistry.register(ref.key(), ref.value(), RegistrationInfo.BUILT_IN)
        );
        Registry<Biome> frozenBiomeRegistry = biomeRegistry.freeze();

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
            } else if (arg.startsWith("surface:")) {
                String[] parts = arg.substring("surface:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpSurfaceChunk(seed, cx, cz, generator, randomState, noAquiferSettings,
                                 fluidPicker, frozenBiomeRegistry, biomeSource, outDir);
            } else if (arg.startsWith("aquifer:")) {
                String[] parts = arg.substring("aquifer:".length()).split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpAquiferChunk(seed, cx, cz, aquiferGenerator, aquiferRandomState,
                                 aquiferSettingsHolder.value(), aquiferFluidPicker,
                                 frozenBiomeRegistry, biomeSource, outDir);
            } else if (arg.equals("blocks")) {
                Path blocksResDir = Paths.get("furnace-server", "src", "main", "resources", "blocks");
                dumpBlocks(blocksResDir);
            } else {
                String[] parts = arg.split(":");
                int cx = Integer.parseInt(parts[0]);
                int cz = Integer.parseInt(parts[1]);
                dumpChunk(seed, cx, cz, generator, randomState, noAquiferSettings, fluidPicker, frozenBiomeRegistry, outDir);
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
