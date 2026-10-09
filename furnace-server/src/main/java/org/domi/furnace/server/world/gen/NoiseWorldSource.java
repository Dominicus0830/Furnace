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
public final class NoiseWorldSource implements WorldSource, org.domi.furnace.server.world.gen.structure.TerrainAccess {

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
    private final org.domi.furnace.server.world.gen.structure.StructureEngine structureEngine;
    private final List<SpawnTarget> spawnTargets;
    private final ChunkPipeline pipeline;
    private volatile SpawnPoint defaultSpawn;
    // worldSurfaceHeightAt 캐시 — 청크당 SURFACE 높이 256개(16x16)를 1회만 generate()로 계산해 재사용.
    // 키: (cx<<32)^cz, 값: lz*16+lx 인덱스의 (height-1). generate()는 seed+좌표만의 순수 함수라 메모이제이션 안전.
    // bounded LRU(256) — 무제한 ConcurrentHashMap이던 것을 pristine 캐시와 같은 이유로 bounded화.
    private static final int SURFACE_HEIGHT_CACHE_SIZE = 256;
    private final Map<Long, ChunkColumn> terrainCache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, ChunkColumn> eldest) {
                return size() > 24;
            }
        });
    private WorldgenData worldgenData;
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
                              org.domi.furnace.server.world.gen.structure.StructureEngine structureEngine,
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
        this.structureEngine    = structureEngine;
        structureEngine.setRingTerrain(this);
        if (featurePlacer != null) {
            featurePlacer.setStructureEngine(structureEngine);
            structureEngine.setPlacedFeatureSource(id -> featurePlacer.biomeData().placedFeatureById(id));
        }
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
    /** 차원별 생성 설정 — 노이즈 설정 id, 차원 높이(dimension_type), 바이옴 파라미터 목록. */
    public enum Dimension {
        OVERWORLD("minecraft:overworld", -64, 24, "biome_parameters.json"),
        NETHER("minecraft:nether", 0, 16, "biome_parameters_nether.json"),
        END("minecraft:end", 0, 16, null);

        final String noiseSettings;
        final int minY;
        final int sectionCount;
        final String biomeParameters;

        Dimension(String noiseSettings, int minY, int sectionCount, String biomeParameters) {
            this.noiseSettings = noiseSettings;
            this.minY = minY;
            this.sectionCount = sectionCount;
            this.biomeParameters = biomeParameters;
        }
    }

    /**
     * 오버월드 NoiseWorldSource 를 생성한다.
     *
     * @param seed       월드 시드
     * @param reg        블록 레지스트리 (state id 해석용)
     * @param registries 바이옴 레지스트리 (BiomeSampler 로드용)
     */
    public static NoiseWorldSource overworld(long seed, BlockRegistry reg, RegistrySet registries) {
        return create(Dimension.OVERWORLD, seed, reg, registries);
    }

    public static NoiseWorldSource nether(long seed, BlockRegistry reg, RegistrySet registries) {
        return create(Dimension.NETHER, seed, reg, registries);
    }

    public static NoiseWorldSource end(long seed, BlockRegistry reg, RegistrySet registries) {
        return create(Dimension.END, seed, reg, registries);
    }

    public static NoiseWorldSource create(Dimension dim, long seed, BlockRegistry reg, RegistrySet registries) {
        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        JsonObject settingsJson = data.noiseSettings(dim.noiseSettings);
        NoiseSettings settings = NoiseSettings.fromJson(dim.noiseSettings, settingsJson);
        NoiseRouter router = NoiseRouter.build(data, settings, seed);

        int stoneId = reg.defaultStateId(settings.defaultBlock());  // overworld stone, nether netherrack
        int waterId = reg.defaultStateId(settings.defaultFluid());  // 기본 유체(네더는 용암)
        int lavaId  = reg.defaultStateId("minecraft:lava");

        JsonArray params = switch (dim) {
            case OVERWORLD -> data.biomeParameters();
            case NETHER -> data.resourceArray(dim.biomeParameters);
            case END -> endBiomeList();
        };
        BiomeSampler biomeSampler = dim == Dimension.END ? BiomeSampler.theEnd(registries, router, dim.sectionCount)
                                                         : BiomeSampler.load(params, registries, dim.sectionCount);

        java.util.List<String> biomeEntries = registries.entries("minecraft:worldgen/biome");
        String[] registryBiomeNames = biomeEntries.toArray(new String[0]);
        int biomeCount = registryBiomeNames.length;
        int biomeDirectBits = Math.max(1, 32 - Integer.numberOfLeadingZeros(biomeCount - 1));
        SurfaceSystem surfaceSystem = SurfaceSystem.create(data, settings, reg, seed,
            registryBiomeNames, biomeSampler, router);

        OverworldCarvers overworldCarvers = switch (dim) {
            case OVERWORLD -> OverworldCarvers.load();
            case NETHER -> OverworldCarvers.loadNether();
            case END -> OverworldCarvers.none();
        };
        // FeatureSorter index는 vanilla possibleBiomes() 순서에 의존(=파라미터 목록 첫등장 distinct).
        // registry 전체 순서를 쓰면 다른 차원 biome이 섞여 광석 index가 어긋난다.
        List<String> possible = possibleBiomeOrder(params, registries);
        // WorldGenerationContext: minGenY = max(차원 min, 생성기 min), genDepth = min(차원 높이, 노이즈 높이)
        int genMinY = Math.max(dim.minY, settings.minY());
        int genDepth = Math.min(dim.sectionCount * 16, settings.height());
        FeaturePlacer featurePlacer = loadFeaturePlacer(data, possible, biomeEntries, reg, seed, genMinY, genDepth, settings.seaLevel(), router);
        StructurePlacementCatalog structurePlacementCatalog = StructurePlacementCatalog.load(data);
        List<SpawnTarget> spawnTargets = loadSpawnTargets(settingsJson);
        org.domi.furnace.server.world.gen.structure.StructureEngine structureEngine =
            new org.domi.furnace.server.world.gen.structure.StructureEngine(reg, seed, new java.util.HashSet<>(possible));

        structureEngine.setBiomeRegistryNames(registryBiomeNames);
        NoiseWorldSource source = new NoiseWorldSource(router, settings, biomeSampler, surfaceSystem,
                                    registryBiomeNames, stoneId, waterId, lavaId,
                                    seed, reg, biomeDirectBits, overworldCarvers,
                                    featurePlacer, structurePlacementCatalog, structureEngine, spawnTargets);
        source.worldgenData = data;
        source.dimension = dim;
        return source;
    }

    private Dimension dimension = Dimension.OVERWORLD;

    /** TheEndBiomeSource.possibleBiomes 순서(the_end, highlands, midlands, small islands, barrens) — 파라미터 목록 형식으로. */
    private static JsonArray endBiomeList() {
        JsonArray a = new JsonArray();
        for (String b : new String[]{"minecraft:the_end", "minecraft:end_highlands", "minecraft:end_midlands", "minecraft:small_end_islands", "minecraft:end_barrens"}) {
            JsonObject o = new JsonObject();
            o.addProperty("biome", b);
            a.add(o);
        }
        return a;
    }

    public Dimension dimension() { return dimension; }

    /** vanilla possibleBiomes() 순서 = biome_parameters.json 의 biome 첫등장 distinct (registry 존재만). */
    private static List<String> possibleBiomeOrder(JsonArray params, RegistrySet registries) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        for (com.google.gson.JsonElement el : params) {
            String name = el.getAsJsonObject().get("biome").getAsString();
            if (registries.indexOf("minecraft:worldgen/biome", name) < 0) continue;
            seen.add(name);
        }
        return new java.util.ArrayList<>(seen);
    }

    private static FeaturePlacer loadFeaturePlacer(
            WorldgenData data, List<String> possibleBiomes, List<String> registryOrder, BlockRegistry reg, long seed,
            int genMinY, int genDepth, int seaLevel, NoiseRouter router) {
        try {
            BiomeFeatureData biomeFeatureData = BiomeFeatureData.load(data, possibleBiomes, registryOrder, reg, genMinY, genDepth, seaLevel);
            return new FeaturePlacer(seed, biomeFeatureData, FeatureSorter.build(biomeFeatureData), reg, router.random());
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

    /** generate()가 이미 샘플한 biome id를 함께 돌려줘, 호출부가 같은 청크의 biome을 다시 샘플하지 않게 한다. */
    public record Generated(ChunkColumn col, int[] biomeIds) {}

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        return generateWithBiomes(chunkX, chunkZ).col();
    }

    public Generated generateWithBiomes(int chunkX, int chunkZ) {
        return generateWithBiomes(chunkX, chunkZ, null);
    }

    /** 실제 월드 청크용: 주변 terrain_adaptation 구조물의 Beardifier 를 적용한 지형. 높이 질의용 generate() 는 바닐라처럼 beard 없음. */
    public Generated generateWorldChunk(int chunkX, int chunkZ) {
        return generateWithBiomes(chunkX, chunkZ, beardifierFor(chunkX, chunkZ));
    }

    // 시작점 캐시(Beardifier 전용 — 장식에 쓰지 않으므로 조각 상태가 바뀌지 않는다)
    private final Map<Long, List<org.domi.furnace.server.world.gen.structure.StructureStart>> beardStartCache =
        java.util.Collections.synchronizedMap(new java.util.LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, List<org.domi.furnace.server.world.gen.structure.StructureStart>> eldest) {
                return size() > 4096;
            }
        });

    /** Beardifier.forStructuresInChunk — 반경 8 청크 시작점 중 (확장된) 상자가 이 청크와 겹치는 terrain_adaptation 구조물. */
    public org.domi.furnace.server.world.gen.structure.Beardifier beardifierFor(int cx, int cz) {
        List<org.domi.furnace.server.world.gen.structure.StructureStart> refs = new ArrayList<>();
        int minX = cx * 16, minZ = cz * 16;
        for (int dz = -8; dz <= 8; dz++) {
            for (int dx = -8; dx <= 8; dx++) {
                int nx = cx + dx, nz = cz + dz;
                List<org.domi.furnace.server.world.gen.structure.StructureStart> starts = beardStartCache.get(chunkKey(nx, nz));
                if (starts == null) {
                    starts = applyStructureStarts(nx, nz);
                    beardStartCache.put(chunkKey(nx, nz), starts);
                }
                for (var s : starts) {
                    if ("none".equals(s.terrainAdaptation())) continue;
                    var b = s.boundingBox();
                    if (b.maxX() >= minX && b.minX() <= minX + 15 && b.maxZ() >= minZ && b.minZ() <= minZ + 15) refs.add(s);
                }
            }
        }
        if (refs.isEmpty()) return null;
        return org.domi.furnace.server.world.gen.structure.Beardifier.forChunk(refs, cx, cz);
    }

    private Generated generateWithBiomes(int chunkX, int chunkZ, org.domi.furnace.server.world.gen.structure.Beardifier beard) {
        int minY      = settings.minY();       // -64
        int height    = settings.height();     // 384
        int seaLevel  = settings.seaLevel();   // 63
        int lavaFloor = settings.lavaFloorY(); // -54

        NoiseChunk nc = new NoiseChunk(chunkX, chunkZ, router, settings, reg);
        Aquifer aquifer = settings.aquifersEnabled()
            ? new NoiseBasedAquifer(nc, chunkX, chunkZ, router, settings, router.aquiferRandom(), stoneId, waterId, lavaId)
            : new DisabledAquifer(seaLevel, lavaFloor, waterId, lavaId);
        nc.setBeardifier(beard);
        int[] blockIds = nc.generateBlockIds(stoneId, aquifer);

        ChunkColumn col = new ChunkColumn(0, reg.directBits(), biomeDirectBits, dimension.minY, dimension.sectionCount);
        if (blockIds.length != dimension.sectionCount * 4096) {
            // 노이즈 높이가 차원 높이보다 낮다(네더 0..127 / 차원 0..255) — 나머지는 공기.
            int[] full = new int[dimension.sectionCount * 4096];
            System.arraycopy(blockIds, 0, full, (minY - dimension.minY) * 256, blockIds.length);
            blockIds = full;
        }

        int[] biomeIds = biomeSampler.sampleChunk(chunkX, chunkZ, nc);
        col.loadGenerated(blockIds, biomeIds);

        surfaceSystem.buildSurface(col, biomeIds, nc, chunkX, chunkZ);
        col.rebuildHeightmapsAfterSurface();
        col.clearDirty();

        return new Generated(col, biomeIds);
    }

    /** 청크 biome id 배열에 등장하는 biome 이름 집합 — FeaturePlacer의 neighborBiomes 구성 단위(청크당 한 번만 계산해 재사용). */
    public Set<String> biomeNamesOf(int[] biomeIds) {
        boolean[] seen = new boolean[registryBiomeNames.length];
        HashSet<String> names = new HashSet<>();
        for (int biomeId : biomeIds) {
            if (biomeId >= 0 && biomeId < registryBiomeNames.length && !seen[biomeId]) {
                seen[biomeId] = true;
                names.add(registryBiomeNames[biomeId]);
            }
        }
        return names;
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

    /** 디버그: quart 좌표(월드 quart x, 월드 quart y=블록y/4, 월드 quart z)의 기후 타깃 6개(온도·습도·대륙성·침식·깊이·기이함). */
    public long[] climateTarget(int quartX, int quartY, int quartZ) {
        org.domi.furnace.server.world.gen.df.DensityFunction.SinglePointContext ctx =
            new org.domi.furnace.server.world.gen.df.DensityFunction.SinglePointContext(quartX * 4, quartY * 4, quartZ * 4);
        return new long[] {
            BiomeSampler.quantize((float) router.temperature().compute(ctx)),
            BiomeSampler.quantize((float) router.humidity().compute(ctx)),
            BiomeSampler.quantize((float) router.continents().compute(ctx)),
            BiomeSampler.quantize((float) router.erosion().compute(ctx)),
            BiomeSampler.quantize((float) router.depth().compute(ctx)),
            BiomeSampler.quantize((float) router.ridges().compute(ctx))
        };
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
        return structureEngine.createStarts(cx, cz, this);
    }

    public org.domi.furnace.server.world.gen.structure.StructureEngine structureEngine() {
        return structureEngine;
    }

    // ---- TerrainAccess ----
    @Override public int seaLevel() { return settings.seaLevel(); }

    @Override public int minY() { return settings.minY(); }

    @Override public int genDepth() { return settings.height(); }

    @Override
    public int firstOccupiedHeight(int heightmap, int blockX, int blockZ) {
        return heightmap == HM_OCEAN_FLOOR_WG ? oceanFloorWgHeightAt(blockX, blockZ) : worldSurfaceHeightAt(blockX, blockZ);
    }

    @Override
    public int baseBlock(int blockX, int blockY, int blockZ) {
        int cx = Math.floorDiv(blockX, 16);
        int cz = Math.floorDiv(blockZ, 16);
        ChunkColumn c = terrainCache.computeIfAbsent(chunkKey(cx, cz), k -> generate(cx, cz));
        return c.getBlock(blockX - cx * 16, blockY, blockZ - cz * 16);
    }

    @Override
    public boolean coldEnoughToSnow(String biome, int x, int y, int z) {
        return BiomeClimate.coldEnoughToSnow(worldgenData.biomeTemperature(biome), worldgenData.biomeFrozenModifier(biome), x, y, z);
    }

    @Override
    public String biomeAt(int blockX, int blockY, int blockZ) {
        return biomeNameAt(blockX, blockY, blockZ);
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

    /** OCEAN_FLOOR_WG 상당 getFirstOccupiedHeight — 같은 청크 캐시의 뒤쪽 256칸. */
    int oceanFloorWgHeightAt(int blockX, int blockZ) {
        int cx = Math.floorDiv(blockX, 16);
        int cz = Math.floorDiv(blockZ, 16);
        int[] heights = surfaceHeightCache.computeIfAbsent(chunkKey(cx, cz), k -> computeSurfaceHeights(cx, cz));
        return heights[256 + (blockZ - cz * 16) * 16 + (blockX - cx * 16)];
    }

    private int[] computeSurfaceHeights(int cx, int cz) {
        ChunkColumn c = generate(cx, cz);
        int[] heights = new int[512];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                heights[lz * 16 + lx] = height(c, lx, lz, HeightKind.WORLD_SURFACE) - 1;
                heights[256 + lz * 16 + lx] = height(c, lx, lz, HeightKind.OCEAN_FLOOR) - 1;
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
