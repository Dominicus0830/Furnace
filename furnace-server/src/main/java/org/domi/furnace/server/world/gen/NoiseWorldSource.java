// 바닐라 비트일치 노이즈 지형 WorldSource. generateBlockIds + Aquifer 기반 블록 결정
package org.domi.furnace.server.world.gen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.SpawnPoint;
import org.domi.furnace.server.world.gen.carver.CarvingMask;
import org.domi.furnace.server.world.gen.carver.OverworldCarvers;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.feature.BiomeFeatureData;
import org.domi.furnace.server.world.gen.feature.FeaturePlacer;
import org.domi.furnace.server.world.gen.feature.FeatureSorter;
import org.domi.furnace.server.world.gen.feature.FeatureWorldView;
import org.domi.furnace.server.world.gen.pipeline.ChunkPipeline;
import org.domi.furnace.server.world.gen.structure.StructurePlacementCatalog;
import org.domi.furnace.server.world.gen.structure.StructureSetPlacement;
import org.domi.furnace.server.world.gen.surface.SurfaceSystem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * NoiseChunk 밀도 엔진을 사용해 바닐라 1.21.1 오버월드 지형을 생성하는 WorldSource.
 *
 * 블록 결정 규칙 (바닐라 NoiseBasedChunkGenerator.doFill 동일):
 *   final_density > 0  → stone (defaultBlock)
 *   else y < lavaFloorY(-54) → lava
 *   else y < seaLevel(63)    → water (defaultFluid)
 *   else                     → air
 *
 * router/settings/ids 는 생성 시 한 번만 빌드하며 generate() 는 thread-safe.
 */
public final class NoiseWorldSource implements WorldSource {

    private final NoiseRouter router;
    private final NoiseSettings settings;
    private final BiomeSampler biomeSampler;
    private final SurfaceSystem surfaceSystem;
    private final String[] registryBiomeNames;
    private final int stoneId;
    private final int waterId;
    private final int lavaId;
    private final long seed;
    private final BlockRegistry reg;
    private final int biomeDirectBits;
    private final OverworldCarvers overworldCarvers;
    private final FeaturePlacer featurePlacer;
    private final StructurePlacementCatalog structurePlacementCatalog;
    private final List<SpawnTarget> spawnTargets;
    private final ChunkPipeline pipeline;
    private volatile SpawnPoint defaultSpawn;
    // worldSurfaceHeightAt 캐시 — 청크당 SURFACE 높이 256개(16x16)를 1회만 generate()로 계산해 재사용.
    // 키: (cx<<32)^cz, 값: lz*16+lx 인덱스의 (height-1). generate()는 seed+좌표만의 순수 함수라 메모이제이션 안전.
    // bounded LRU(256) — 무제한 ConcurrentHashMap이던 것을 pristine 캐시와 같은 이유로 bounded화.
    private static final int SURFACE_HEIGHT_CACHE_SIZE = 256;
    private final Map<Long, int[]> surfaceHeightCache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
                return size() > SURFACE_HEIGHT_CACHE_SIZE;
            }
        });

    private NoiseWorldSource(NoiseRouter router, NoiseSettings settings, BiomeSampler biomeSampler,
                              SurfaceSystem surfaceSystem, String[] registryBiomeNames,
                              int stoneId, int waterId, int lavaId,
                              long seed, BlockRegistry reg, int biomeDirectBits, OverworldCarvers overworldCarvers,
                              FeaturePlacer featurePlacer, StructurePlacementCatalog structurePlacementCatalog,
                              List<SpawnTarget> spawnTargets) {
        this.router             = router;
        this.settings           = settings;
        this.biomeSampler       = biomeSampler;
        this.surfaceSystem      = surfaceSystem;
        this.registryBiomeNames = registryBiomeNames;
        this.stoneId            = stoneId;
        this.waterId            = waterId;
        this.lavaId             = lavaId;
        this.seed               = seed;
        this.reg                = reg;
        this.biomeDirectBits    = biomeDirectBits;
        this.overworldCarvers   = overworldCarvers;
        this.featurePlacer      = featurePlacer;
        this.structurePlacementCatalog = structurePlacementCatalog;
        this.spawnTargets       = spawnTargets;
        this.pipeline           = new ChunkPipeline(this);
    }

    /**
     * 오버월드 NoiseWorldSource 를 생성한다.
     *
     * @param seed       월드 시드
     * @param reg        블록 레지스트리 (state id 해석용)
     * @param registries 바이옴 레지스트리 (BiomeSampler 로드용)
     */
    public static NoiseWorldSource overworld(long seed, BlockRegistry reg, RegistrySet registries) {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        NoiseSettings settings = NoiseSettings.fromJson(data.noiseSettings("minecraft:overworld"));
        NoiseRouter router = NoiseRouter.build(data, settings, seed);

        int stoneId = reg.defaultStateId(settings.defaultBlock());  // minecraft:stone
        int waterId = reg.defaultStateId(settings.defaultFluid());  // minecraft:water
        int lavaId  = reg.defaultStateId("minecraft:lava");

        BiomeSampler biomeSampler = BiomeSampler.load(data, registries);

        java.util.List<String> biomeEntries = registries.entries("minecraft:worldgen/biome");
        String[] registryBiomeNames = biomeEntries.toArray(new String[0]);
        int biomeCount = registryBiomeNames.length;
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, reg, seed,
            registryBiomeNames, biomeSampler, router);

        OverworldCarvers overworldCarvers = OverworldCarvers.load();
        // FeatureSorter index는 vanilla possibleBiomes() 순서에 의존(=biome_parameters.json 첫등장 distinct).
        // registry 전체 순서를 쓰면 nether/end biome이 섞여 광석 index가 어긋난다.
        FeaturePlacer featurePlacer = loadFeaturePlacer(data, possibleBiomeOrder(data, registries), biomeEntries, reg, seed);
        StructurePlacementCatalog structurePlacementCatalog = StructurePlacementCatalog.load(data);
        List<SpawnTarget> spawnTargets = loadSpawnTargets(data.noiseSettings("minecraft:overworld"));

        return new NoiseWorldSource(router, settings, biomeSampler, surfaceSystem,
                                    registryBiomeNames, stoneId, waterId, lavaId,
                                    seed, reg, biomeDirectBits, overworldCarvers,
                                    featurePlacer, structurePlacementCatalog, spawnTargets);
    }

    /** vanilla possibleBiomes() 순서 = biome_parameters.json 의 biome 첫등장 distinct (registry 존재만). */
    private static List<String> possibleBiomeOrder(WorldgenData data, RegistrySet registries) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (com.google.gson.JsonElement el : data.biomeParameters()) {
            String name = el.getAsJsonObject().get("biome").getAsString();
            if (registries.indexOf("minecraft:worldgen/biome", name) < 0) continue;
            seen.add(name);
        }
        return new java.util.ArrayList<>(seen);
    }

    private static FeaturePlacer loadFeaturePlacer(
            WorldgenData data, List<String> possibleBiomes, List<String> registryOrder, BlockRegistry reg, long seed) {
        try {
            BiomeFeatureData biomeFeatureData = BiomeFeatureData.load(data, possibleBiomes, registryOrder, reg);
            return new FeaturePlacer(seed, biomeFeatureData, FeatureSorter.build(biomeFeatureData), reg);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Worldgen resource not found:")) {
                return null;
            }
            throw e;
        }
    }

    public SpawnPoint defaultSpawn() {
        SpawnPoint cached = defaultSpawn;
        if (cached != null) return cached;
        synchronized (this) {
            if (defaultSpawn == null) defaultSpawn = computeDefaultSpawn();
            return defaultSpawn;
        }
    }

    private SpawnPoint computeDefaultSpawn() {
        SpawnSearchResult climateSpawn = findClimateSpawnPosition();
        int spawnChunkX = Math.floorDiv(climateSpawn.x(), 16);
        int spawnChunkZ = Math.floorDiv(climateSpawn.z(), 16);

        int fallbackX = spawnChunkX * 16 + 8;
        int fallbackY = 64;
        int fallbackZ = spawnChunkZ * 16 + 8;
        SpawnPoint fallback = new SpawnPoint(fallbackX, fallbackY, fallbackZ, 0.0f);

        int j = 0;
        int k = 0;
        int l = 0;
        int i1 = -1;
        for (int j1 = 0; j1 < 121; j1++) {
            if (j >= -5 && j <= 5 && k >= -5 && k <= 5) {
                SpawnPoint safe = getSpawnPosInChunk(spawnChunkX + j, spawnChunkZ + k);
                if (safe != null) return safe;
            }

            if (j == k || (j < 0 && j == -k) || (j > 0 && j == 1 - k)) {
                int k1 = l;
                l = -i1;
                i1 = k1;
            }

            j += l;
            k += i1;
        }

        return fallback;
    }

    private SpawnPoint getSpawnPosInChunk(int chunkX, int chunkZ) {
        ChunkColumn col = generate(chunkX, chunkZ);
        int minX = chunkX * 16;
        int minZ = chunkZ * 16;
        for (int x = minX; x <= minX + 15; x++) {
            for (int z = minZ; z <= minZ + 15; z++) {
                SpawnPoint pos = getOverworldRespawnPos(col, x, z);
                if (pos != null) return pos;
            }
        }
        return null;
    }

    private SpawnPoint getOverworldRespawnPos(ChunkColumn col, int blockX, int blockZ) {
        int lx = blockX & 15;
        int lz = blockZ & 15;
        int motionBlocking = height(col, lx, lz, HeightKind.MOTION_BLOCKING);
        if (motionBlocking < settings.minY()) return null;

        int worldSurface = height(col, lx, lz, HeightKind.WORLD_SURFACE);
        int oceanFloor = height(col, lx, lz, HeightKind.OCEAN_FLOOR);
        if (worldSurface <= motionBlocking && worldSurface > oceanFloor) return null;

        for (int y = motionBlocking + 1; y >= settings.minY(); y--) {
            int state = col.getBlock(lx, y, lz);
            if (isFluid(state)) break;
            if (isSolid(state)) return new SpawnPoint(blockX, y + 1, blockZ, 0.0f);
        }
        return null;
    }

    private int height(ChunkColumn col, int lx, int lz, HeightKind kind) {
        int maxY = settings.minY() + settings.height() - 1;
        for (int y = maxY; y >= settings.minY(); y--) {
            int state = col.getBlock(lx, y, lz);
            boolean hit = switch (kind) {
                case WORLD_SURFACE, MOTION_BLOCKING -> state != ChunkSection.AIR;
                case OCEAN_FLOOR -> isSolid(state);
            };
            if (hit) return y + 1;
        }
        return settings.minY();
    }

    private boolean isFluid(int state) {
        return state == waterId || state == lavaId;
    }

    private boolean isSolid(int state) {
        return state != ChunkSection.AIR && !isFluid(state);
    }

    private SpawnSearchResult findClimateSpawnPosition() {
        if (spawnTargets.isEmpty()) return new SpawnSearchResult(0, 0, Long.MAX_VALUE);
        SpawnSearchResult result = spawnFitness(0, 0);
        result = radialSearch(result, 2048.0f, 512.0f);
        return radialSearch(result, 512.0f, 32.0f);
    }

    private SpawnSearchResult radialSearch(SpawnSearchResult current, float maxDistance, float step) {
        float angle = 0.0f;
        float distance = step;
        int originX = current.x();
        int originZ = current.z();
        SpawnSearchResult best = current;

        while (distance <= maxDistance) {
            int x = originX + (int)(Math.sin(angle) * distance);
            int z = originZ + (int)(Math.cos(angle) * distance);
            SpawnSearchResult candidate = spawnFitness(x, z);
            if (candidate.fitness() < best.fitness()) best = candidate;

            angle += step / distance;
            if (angle > Math.PI * 2.0) {
                angle = 0.0f;
                distance += step;
            }
        }
        return best;
    }

    private SpawnSearchResult spawnFitness(int x, int z) {
        double distanceSquared = (double)x * (double)x + (double)z * (double)z;
        long distancePenalty = (long)(100_000_000.0 * Math.pow(distanceSquared / 6_250_000.0, 2.0));

        DensityFunction.SinglePointContext ctx =
            new DensityFunction.SinglePointContext((x >> 2) * 4, 0, (z >> 2) * 4);
        long[] target = {
            BiomeSampler.quantize((float) router.temperature().compute(ctx)),
            BiomeSampler.quantize((float) router.humidity().compute(ctx)),
            BiomeSampler.quantize((float) router.continents().compute(ctx)),
            BiomeSampler.quantize((float) router.erosion().compute(ctx)),
            0L,
            BiomeSampler.quantize((float) router.ridges().compute(ctx))
        };

        long best = Long.MAX_VALUE;
        for (SpawnTarget spawnTarget : spawnTargets) {
            best = Math.min(best, spawnTarget.fitness(target));
        }
        return new SpawnSearchResult(x, z, distancePenalty + best);
    }

    private static List<SpawnTarget> loadSpawnTargets(JsonObject noiseSettings) {
        JsonArray targets = noiseSettings.getAsJsonArray("spawn_target");
        List<SpawnTarget> result = new ArrayList<>(targets.size());
        for (JsonElement el : targets) {
            JsonObject obj = el.getAsJsonObject();
            result.add(new SpawnTarget(
                parameter(obj.get("temperature")),
                parameter(obj.get("humidity")),
                parameter(obj.get("continentalness")),
                parameter(obj.get("erosion")),
                parameter(obj.get("depth")),
                parameter(obj.get("weirdness")),
                BiomeSampler.quantize(obj.get("offset").getAsFloat())
            ));
        }
        return List.copyOf(result);
    }

    private static Parameter parameter(JsonElement element) {
        if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            return new Parameter(BiomeSampler.quantize(arr.get(0).getAsFloat()),
                BiomeSampler.quantize(arr.get(1).getAsFloat()));
        }
        long point = BiomeSampler.quantize(element.getAsFloat());
        return new Parameter(point, point);
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        int minY      = settings.minY();       // -64
        int height    = settings.height();     // 384
        int seaLevel  = settings.seaLevel();   // 63
        int lavaFloor = settings.lavaFloorY(); // -54

        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings, reg);
        Aquifer aquifer = settings.aquifersEnabled()
            ? new NoiseBasedAquifer(nc, chunkX, chunkZ, router, settings, router.aquiferRandom(), stoneId, waterId, lavaId)
            : new DisabledAquifer(seaLevel, lavaFloor, waterId, lavaId);
        int[] blockIds = nc.generateBlockIds(stoneId, aquifer);

        ChunkColumn col = new ChunkColumn(0, reg.directBits(), biomeDirectBits);

        int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, nc);
        col.loadGenerated(blockIds, biomeIds);

        surfaceSystem.buildSurface(col, biomeIds, nc, chunkX, chunkZ);
        col.clearDirty();

        return col;
    }

    private void applyCarvers(ChunkColumn col, int chunkX, int chunkZ, NoiseChunk nc) {
        CarvingMask mask = new CarvingMask(settings.minY(), settings.height());
        Aquifer aquifer = settings.aquifersEnabled()
            ? new NoiseBasedAquifer(nc, chunkX, chunkZ, router, settings, router.aquiferRandom(), stoneId, waterId, lavaId)
            : new DisabledAquifer(settings.seaLevel(), settings.lavaFloorY(), waterId, lavaId);

        WorldgenRandom worldgenRandom = new WorldgenRandom();
        // carve regrass용 스냅샷 — vanilla WORLD_SURFACE_WG처럼 carve 이전(buildSurface 직후) 상태로 고정.
        int[] surfaceYGrid = surfaceSystem.surfaceYGrid(col);

        for (int originX = chunkX - 8; originX <= chunkX + 8; originX++) {
            for (int originZ = chunkZ - 8; originZ <= chunkZ + 8; originZ++) {
                org.domi.furnace.server.world.gen.carver.WorldCarver.ChunkPos originPos =
                    new org.domi.furnace.server.world.gen.carver.WorldCarver.ChunkPos(originX, originZ);
                int carverIndex = 0;
                for (OverworldCarvers.Entry entry : overworldCarvers.entries()) {
                    worldgenRandom.setLargeFeatureSeed(this.seed + carverIndex, originX, originZ);
                    if (entry.carver().isStartChunk(entry.config(), worldgenRandom)) {
                        entry.carver().carve(col, entry.config(), chunkX, chunkZ,
                            settings, aquifer, reg, mask, worldgenRandom, originPos,
                            surfaceSystem, nc, surfaceYGrid);
                    }
                    carverIndex++;
                }
            }
        }
        col.freezeWgHeightmaps();
    }

    public void applyCarvers(ChunkColumn col, int cx, int cz) {
        NoiseChunk nc = new NoiseChunk(cx, cz, router, settings, reg);
        applyCarvers(col, cx, cz, nc);
    }

    public int[] sampleBiomes(int cx, int cz) {
        NoiseChunk nc = new NoiseChunk(cx, cz, router, settings, reg);
        return biomeSampler.sampleChunk(cx, cz, nc);
    }

    public ChunkColumn generateWithCarvers(int chunkX, int chunkZ) {
        ChunkColumn col = generate(chunkX, chunkZ);
        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings, reg);
        applyCarvers(col, chunkX, chunkZ, nc);
        return col;
    }

    /**
     * M4g-e S2a — placement 판정된 청크에서 지원 구조물(현재 desert_pyramid)의 StructureStart 를 생성한다.
     * 블록은 쓰지 않는다(S2b 이후). 높이 조회는 매 호출마다 해당 청크를 SURFACE 상태로 재생성해 조회한다(캐시는 S3+).
     */
    public List<org.domi.furnace.server.world.gen.structure.StructureStart> applyStructureStarts(int cx, int cz) {
        List<StructureSetPlacement> placements = structurePlacementCatalog.placementsForChunk(seed, cx, cz);
        if (placements.isEmpty()) return List.of();

        List<org.domi.furnace.server.world.gen.structure.StructureStart> result = new ArrayList<>();
        for (StructureSetPlacement set : placements) {
            for (String structureId : set.structureIds()) {
                if (!org.domi.furnace.server.world.gen.structure.DesertPyramidStructure.ID.equals(structureId)) {
                    continue; // 다른 구조물은 S4+ 범위
                }
                org.domi.furnace.server.world.gen.structure.DesertPyramidStructure.generate(
                    seed, cx, cz, settings.seaLevel(), this::worldSurfaceHeightAt, this::biomeNameAt
                ).ifPresent(result::add);
            }
        }
        return result;
    }

    /**
     * vanilla getFirstOccupiedHeight(WORLD_SURFACE_WG) 근사 — 대상 청크를 SURFACE 상태로 생성해 최상단 비-공기 y 조회.
     * 청크당 결과를 {@link #surfaceHeightCache}에 메모이제이션해 동일 청크 반복 조회 시 재생성(42ms 실측)을 제거한다.
     */
    int worldSurfaceHeightAt(int blockX, int blockZ) {
        int cx = Math.floorDiv(blockX, 16);
        int cz = Math.floorDiv(blockZ, 16);
        int[] heights = surfaceHeightCache.computeIfAbsent(chunkKey(cx, cz), k -> computeSurfaceHeights(cx, cz));
        int lx = blockX - cx * 16;
        int lz = blockZ - cz * 16;
        return heights[lz * 16 + lx];
    }

    private int[] computeSurfaceHeights(int cx, int cz) {
        ChunkColumn c = generate(cx, cz);
        int[] heights = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                heights[lz * 16 + lx] = height(c, lx, lz, HeightKind.WORLD_SURFACE) - 1;
            }
        }
        return heights;
    }

    private static long chunkKey(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /** 테스트 전용 — surfaceHeightCache에 실제로 캐시된(재생성이 아닌) 청크 수. */
    int surfaceHeightCacheSize() {
        return surfaceHeightCache.size();
    }

    /** 테스트 전용 — 캐시 전/후 실측 비교를 위해 캐시 맵 자체를 노출. */
    Map<Long, int[]> surfaceHeightCache() {
        return surfaceHeightCache;
    }

    /** vanilla getNoiseBiome(quart 좌표) — 구조물 validBiome 판정용. */
    private String biomeNameAt(int blockX, int blockY, int blockZ) {
        int qx = Math.floorDiv(blockX, 4);
        int qy = Math.floorDiv(blockY, 4);
        int qz = Math.floorDiv(blockZ, 4);
        int id = biomeSampler.sampleQuart(qx, qy, qz, router);
        return (id >= 0 && id < registryBiomeNames.length) ? registryBiomeNames[id] : "";
    }

    public List<StructureSetPlacement> structurePlacementCandidates(int chunkX, int chunkZ) {
        return structurePlacementCatalog.placementsForChunk(seed, chunkX, chunkZ);
    }

    public ChunkColumn generateWithFeatures(int chunkX, int chunkZ) {
        return pipeline.generateFull(chunkX, chunkZ).join();
    }

    public void decorateCenter(ChunkColumn col, int cx, int cz) {
        if (featurePlacer == null) return;

        NoiseChunk nc = new NoiseChunk(cx, cz, router, settings, reg);
        int[] biomeIds = biomeSampler.sampleChunk(cx, cz, nc);
        FeatureWorldView view = new FeatureWorldView(col, cx, cz, biomeIds, seed);
        featurePlacer.decorate(view, collectNeighborBiomes(cx, cz), cx, cz);
        col.compactSections();
        col.clearDirty();
    }

    public void decorateOrigin(FeatureWorldView view, Set<String> neighborBiomes, int ox, int oz,
                                List<org.domi.furnace.server.world.gen.structure.StructureStart> starts) {
        if (featurePlacer == null) return;
        featurePlacer.decorate(view, neighborBiomes, ox, oz, starts);
    }

    public FeaturePlacer featurePlacer() {
        return featurePlacer;
    }

    public long seed() {
        return seed;
    }

    public Set<String> collectNeighborBiomes(int chunkX, int chunkZ) {
        HashSet<String> result = new HashSet<>();
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int nx = chunkX + dx;
                int nz = chunkZ + dz;
                NoiseChunk noiseChunk = new NoiseChunk(nx, nz, router, settings, reg);
                int[] biomeIds = biomeSampler.sampleChunk(nx, nz, noiseChunk);
                for (int biomeId : biomeIds) {
                    if (biomeId >= 0 && biomeId < registryBiomeNames.length) {
                        result.add(registryBiomeNames[biomeId]);
                    }
                }
            }
        }
        return result;
    }

    private enum HeightKind {
        WORLD_SURFACE,
        MOTION_BLOCKING,
        OCEAN_FLOOR
    }

    private record SpawnSearchResult(int x, int z, long fitness) {}

    private record Parameter(long min, long max) {
        long distance(long value) {
            long above = value - max;
            long below = min - value;
            return above > 0L ? above : Math.max(below, 0L);
        }
    }

    private record SpawnTarget(
        Parameter temperature,
        Parameter humidity,
        Parameter continentalness,
        Parameter erosion,
        Parameter depth,
        Parameter weirdness,
        long offset
    ) {
        long fitness(long[] target) {
            long fit = offset * offset;
            fit += square(temperature.distance(target[0]));
            fit += square(humidity.distance(target[1]));
            fit += square(continentalness.distance(target[2]));
            fit += square(erosion.distance(target[3]));
            fit += square(depth.distance(target[4]));
            fit += square(weirdness.distance(target[5]));
            return fit;
        }

        private static long square(long value) {
            return value * value;
        }
    }
}
