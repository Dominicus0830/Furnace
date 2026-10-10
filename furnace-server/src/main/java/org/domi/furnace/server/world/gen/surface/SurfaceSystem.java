// 표층 규칙을 noise fill 이후 ChunkColumn에 적용하는 오케스트레이터
package org.domi.furnace.server.world.gen.surface;

import org.domi.furnace.server.world.gen.noise.NoiseRandom;
import org.domi.furnace.server.world.gen.noise.PositionalRandom;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.BiomeClimate;
import org.domi.furnace.server.world.gen.BiomeSampler;
import org.domi.furnace.server.world.gen.MathHelper;
import org.domi.furnace.server.world.gen.NoiseChunk;
import org.domi.furnace.server.world.gen.NoiseRouter;
import org.domi.furnace.server.world.gen.NoiseSettings;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.noise.NormalNoise;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.BlockRegistry;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SurfaceSystem {

    private final SurfaceRule rule;
    private final NoiseSettings settings;
    private final WorldgenData worldgenData;
    private final PositionalRandom randomFactory;
    private final Map<String, NormalNoise> noiseCache = new ConcurrentHashMap<>();
    // vertical_gradient용 positional factory 캐시 — fromHashOf는 매 호출 MD5 다이제스트를 수행하는
    // randomName만의 순수 함수라 캐시해도 RNG 스트림 불변 (M-opt P0, buildNoise의 noiseCache와 동일 패턴).
    private final Map<String, PositionalRandom> gradientFactoryCache = new ConcurrentHashMap<>();
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;
    private final int waterId;
    private final int defaultBlockId;
    private final String[] registryBiomeNames;
    private final BiomeSampler biomeSampler;
    private final NoiseRouter router;
    private final long biomeZoomSeed;
    // 배드랜드/빙산 지형 확장용 noise와 clay band 테이블 (바닐라 SurfaceSystem 생성자)
    private final NormalNoise clayBandsOffsetNoise;
    private final NormalNoise badlandsPillarNoise;
    private final NormalNoise badlandsPillarRoofNoise;
    private final NormalNoise badlandsSurfaceNoise;
    private final NormalNoise icebergPillarNoise;
    private final NormalNoise icebergPillarRoofNoise;
    private final NormalNoise icebergSurfaceNoise;
    private final int[] clayBands;
    private final int packedIceId;
    private final int snowBlockId;

    private SurfaceSystem(SurfaceRule rule, NoiseSettings settings,
                          WorldgenData worldgenData, int waterId, int defaultBlockId,
                          long seed, String[] registryBiomeNames,
                          BiomeSampler biomeSampler, NoiseRouter router, BlockRegistry reg) {
        this.rule                = rule;
        this.settings            = settings;
        this.worldgenData        = worldgenData;
        this.waterId             = waterId;
        this.defaultBlockId      = defaultBlockId;
        this.randomFactory       = router.random();   // RandomState.random (legacy 차원은 LegacyPositionalRandom)
        this.surfaceNoise        = buildNoise("minecraft:surface");
        this.surfaceSecondaryNoise = buildNoise("minecraft:surface_secondary");
        this.registryBiomeNames  = registryBiomeNames;
        this.biomeSampler        = biomeSampler;
        this.router              = router;
        this.biomeZoomSeed       = obfuscateSeed(seed);
        this.clayBandsOffsetNoise = buildNoise("minecraft:clay_bands_offset");
        this.badlandsPillarNoise = buildNoise("minecraft:badlands_pillar");
        this.badlandsPillarRoofNoise = buildNoise("minecraft:badlands_pillar_roof");
        this.badlandsSurfaceNoise = buildNoise("minecraft:badlands_surface");
        this.icebergPillarNoise = buildNoise("minecraft:iceberg_pillar");
        this.icebergPillarRoofNoise = buildNoise("minecraft:iceberg_pillar_roof");
        this.icebergSurfaceNoise = buildNoise("minecraft:iceberg_surface");
        this.clayBands = generateBands(randomFactory.fromHashOf("minecraft:clay_bands"), reg);
        this.packedIceId = reg.defaultStateId("minecraft:packed_ice");
        this.snowBlockId = reg.defaultStateId("minecraft:snow_block");
    }

    /** SurfaceSystem.generateBands — 192칸 점토 밴드 테이블. RNG 호출 순서는 바닐라와 동일. */
    private static int[] generateBands(NoiseRandom rnd, BlockRegistry reg) {
        int white = reg.defaultStateId("minecraft:white_terracotta");
        int orange = reg.defaultStateId("minecraft:orange_terracotta");
        int terracotta = reg.defaultStateId("minecraft:terracotta");
        int yellow = reg.defaultStateId("minecraft:yellow_terracotta");
        int brown = reg.defaultStateId("minecraft:brown_terracotta");
        int red = reg.defaultStateId("minecraft:red_terracotta");
        int lightGray = reg.defaultStateId("minecraft:light_gray_terracotta");
        int[] bands = new int[192];
        java.util.Arrays.fill(bands, terracotta);
        for (int i = 0; i < bands.length; i++) {
            i += rnd.nextInt(5) + 1;
            if (i < bands.length) bands[i] = orange;
        }
        makeBands(rnd, bands, 1, yellow);
        makeBands(rnd, bands, 2, brown);
        makeBands(rnd, bands, 1, red);
        int whiteBands = 9 + rnd.nextInt(15 - 9 + 1);   // nextIntBetweenInclusive(9, 15)
        int count = 0;
        for (int pos = 0; count < whiteBands && pos < bands.length; pos += rnd.nextInt(16) + 4) {
            bands[pos] = white;
            if (pos - 1 > 0 && rnd.nextBoolean()) bands[pos - 1] = lightGray;
            if (pos + 1 < bands.length && rnd.nextBoolean()) bands[pos + 1] = lightGray;
            count++;
        }
        return bands;
    }

    private static void makeBands(NoiseRandom rnd, int[] bands, int minWidth, int state) {
        int n = 6 + rnd.nextInt(15 - 6 + 1);            // nextIntBetweenInclusive(6, 15)
        for (int i = 0; i < n; i++) {
            int width = minWidth + rnd.nextInt(3);
            int start = rnd.nextInt(bands.length);
            for (int j = 0; start + j < bands.length && j < width; j++) bands[start + j] = state;
        }
    }

    /** SurfaceSystem.getBand */
    private int getBand(int x, int y, int z) {
        int offset = (int) Math.round(clayBandsOffsetNoise.getValue(x, 0.0, z) * 4.0);
        return clayBands[(y + offset + clayBands.length) % clayBands.length];
    }

    public static SurfaceSystem create(WorldgenData data, NoiseSettings settings,
                                       BlockRegistry reg, long seed,
                                       String[] registryBiomeNames,
                                       BiomeSampler biomeSampler, NoiseRouter router) {
        var noiseSettingsJson = data.noiseSettings(settings.name());
        SurfaceRule rule = new SurfaceRuleParser(reg).parse(
            noiseSettingsJson.get("surface_rule").getAsJsonObject());
        int waterId       = reg.defaultStateId(settings.defaultFluid());
        int defaultBlockId = reg.defaultStateId(settings.defaultBlock());
        return new SurfaceSystem(rule, settings, data, waterId, defaultBlockId, seed,
            registryBiomeNames, biomeSampler, router, reg);
    }

    private NormalNoise buildNoise(String noiseId) {
        return noiseCache.computeIfAbsent(noiseId, id -> {
            NoiseRandom rng = randomFactory.fromHashOf(id);
            var params = worldgenData.noiseParams(id);
            JsonObject p = params.getAsJsonObject();
            int firstOctave = p.get("firstOctave").getAsInt();
            JsonArray amps = p.getAsJsonArray("amplitudes");
            double[] amplitudes = new double[amps.size()];
            for (int i = 0; i < amps.size(); i++) amplitudes[i] = amps.get(i).getAsDouble();
            return NormalNoise.create(rng, new NormalNoise.NoiseParameters(firstOctave, amplitudes));
        });
    }

    public void buildSurface(ChunkColumn col, int[] biomeIds,
                             NoiseChunk nc, int chunkX, int chunkZ) {
        int minY     = settings.minY();
        int maxY     = minY + settings.height() - 1;
        int seaLevel = settings.seaLevel();
        int[][] sectionBlocks = new int[col.sectionCount()][];
        for (int i = 0; i < col.sectionCount(); i++) {
            sectionBlocks[i] = col.sectionBlocks(i);
        }

        int[] surfaceYGrid = surfaceYGrid(col);

        // 4 PSL corners at chunk boundaries (16-block surface cell = one chunk)
        int psl00 = nc.getPreliminarySurfaceLevel(chunkX * 4,     chunkZ * 4);
        int psl10 = nc.getPreliminarySurfaceLevel(chunkX * 4 + 4, chunkZ * 4);
        int psl01 = nc.getPreliminarySurfaceLevel(chunkX * 4,     chunkZ * 4 + 4);
        int psl11 = nc.getPreliminarySurfaceLevel(chunkX * 4 + 4, chunkZ * 4 + 4);

        SurfaceContext ctx = new SurfaceContext();
        ctx.minY          = minY;
        ctx.maxY          = maxY;
        ctx.seaLevel      = seaLevel;
        ctx.randomFactory = randomFactory;
        ctx.worldgenData  = worldgenData;
        ctx.surfaceYGrid  = surfaceYGrid;
        ctx.biomeCache = new HashMap<>();
        ctx.chunkBiomeIds = biomeIds;
        ctx.chunkQuartX0 = chunkX * 4;
        ctx.chunkQuartZ0 = chunkZ * 4;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = chunkX * 16 + lx;
                int worldZ = chunkZ * 16 + lz;

                int columnIndex = lz * 16 + lx;
                int surfaceY = surfaceYGrid[columnIndex];
                ctx.surfaceY  = surfaceY;
                ctx.surfaceDepth = surfaceDepthAt(worldX, worldZ);
                int lerpedPsl = (int) Math.floor(MathHelper.lerp2(
                    lx / 16.0, lz / 16.0, psl00, psl10, psl01, psl11));
                ctx.preliminarySurfaceLevel = lerpedPsl + ctx.surfaceDepth - 8;

                ctx.blockX = worldX;
                ctx.blockZ = worldZ;
                ctx.stoneDepthAbove = 0;
                // waterY: Y one above the topmost fluid block (vanilla waterHeight = i9+1).
                // Integer.MIN_VALUE = no fluid found.
                ctx.waterY = Integer.MIN_VALUE;

                // 바닐라: 컬럼 biome은 (x, heightmap+1, z)에서 조회. eroded_badlands는 규칙 적용 전에 기둥을 먼저 세운다.
                int h = surfaceY + 1;
                String columnBiome = biomeNameAt(worldX, h, worldZ, ctx);
                if (columnBiome.equals("minecraft:eroded_badlands")) {
                    erodedBadlandsExtension(sectionBlocks, columnIndex, worldX, worldZ, h, minY);
                    surfaceYGrid[columnIndex] = topNonAirY(sectionBlocks, columnIndex, minY, maxY);
                }

                // stoneBottomY: cached bottom of current solid column for stoneDepthBelow.
                int stoneBottomY = Integer.MAX_VALUE;
                int sectionIndex = (maxY - col.minY()) >> 4;
                int localY = (maxY - col.minY()) & 15;
                int[] blocks = sectionBlocks[sectionIndex];
                int blockIndex = localY * 256 + columnIndex;

                for (int y = maxY; y >= minY; y--) {
                    int block = blocks[blockIndex];
                    ctx.blockY = y;

                    if (block == ChunkSection.AIR) {
                        ctx.stoneDepthAbove = 0;
                        ctx.waterY = Integer.MIN_VALUE;
                    } else if (block == waterId) {
                        // fluid: update waterHeight only. vanilla does NOT reset stoneDepthAbove on fluid.
                        if (ctx.waterY == Integer.MIN_VALUE) ctx.waterY = y + 1;
                    } else {
                        // solid non-fluid block
                        ctx.stoneDepthAbove++;

                        // compute stoneDepthBelow via cached stoneBottomY
                        if (stoneBottomY >= y) {
                            stoneBottomY = minY;  // assume solid to the bottom of the world
                            int searchSectionIndex = sectionIndex;
                            int searchLocalY = localY - 1;
                            int belowY = y - 1;
                            if (searchLocalY < 0) {
                                searchSectionIndex--;
                                searchLocalY = 15;
                            }
                            while (belowY >= minY && searchSectionIndex >= 0) {
                                int[] searchBlocks = sectionBlocks[searchSectionIndex];
                                int below = searchBlocks[searchLocalY * 256 + columnIndex];
                                if (below == ChunkSection.AIR || below == waterId) {
                                    stoneBottomY = belowY + 1;
                                    break;
                                }
                                belowY--;
                                searchLocalY--;
                                if (searchLocalY < 0 && belowY >= minY) {
                                    searchSectionIndex--;
                                    searchLocalY = 15;
                                }
                            }
                        }
                        ctx.stoneDepthBelow = y - stoneBottomY + 1;

                        // only apply surface rule to the defaultBlock (stone)
                        if (block == defaultBlockId) {
                            ctx.biomeName = null;
                            Integer result = eval(rule, ctx);
                            if (result != null) blocks[blockIndex] = result;
                        }
                    }

                    if (y > minY) {
                        if (localY == 0) {
                            sectionIndex--;
                            localY = 15;
                            blocks = sectionBlocks[sectionIndex];
                            blockIndex = localY * 256 + columnIndex;
                        } else {
                            localY--;
                            blockIndex -= 256;
                        }
                    }
                }

                if (columnBiome.equals("minecraft:frozen_ocean") || columnBiome.equals("minecraft:deep_frozen_ocean")) {
                    frozenOceanExtension(sectionBlocks, columnIndex, ctx.preliminarySurfaceLevel, columnBiome,
                        worldX, worldZ, h, minY, seaLevel);
                    surfaceYGrid[columnIndex] = topNonAirY(sectionBlocks, columnIndex, minY, maxY);
                }
            }
        }
    }

    private String biomeNameAt(int x, int y, int z, SurfaceContext ctx) {
        int biomeId = biomeAtBlock(x, y, z, ctx);
        return (biomeId >= 0 && biomeId < registryBiomeNames.length) ? registryBiomeNames[biomeId] : "minecraft:plains";
    }

    private static int getAt(int[][] sections, int columnIndex, int minY, int y) {
        return sections[(y - minY) >> 4][((y - minY) & 15) * 256 + columnIndex];
    }

    private static void setAt(int[][] sections, int columnIndex, int minY, int y, int state) {
        sections[(y - minY) >> 4][((y - minY) & 15) * 256 + columnIndex] = state;
    }

    /** WORLD_SURFACE_WG: 가장 높은 비공기 블록 y (없으면 minY-1). */
    private static int topNonAirY(int[][] sections, int columnIndex, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            if (getAt(sections, columnIndex, minY, y) != ChunkSection.AIR) return y;
        }
        return minY - 1;
    }

    /** SurfaceSystem.erodedBadlandsExtension */
    private void erodedBadlandsExtension(int[][] sections, int col, int x, int z, int height, int minY) {
        double d = Math.min(Math.abs(badlandsSurfaceNoise.getValue(x, 0.0, z) * 8.25),
            badlandsPillarNoise.getValue(x * 0.2, 0.0, z * 0.2) * 15.0);
        if (d <= 0.0) return;
        double roof = Math.abs(badlandsPillarRoofNoise.getValue(x * 0.75, 0.0, z * 0.75) * 1.5);
        double top = 64.0 + Math.min(d * d * 2.5, Math.ceil(roof * 50.0) + 24.0);
        int topY = (int) Math.floor(top);
        if (height > topY) return;
        for (int y = topY; y >= minY; y--) {
            int state = getAt(sections, col, minY, y);
            if (state == defaultBlockId) break;
            if (state == waterId) return;
        }
        for (int y = topY; y >= minY && getAt(sections, col, minY, y) == ChunkSection.AIR; y--) {
            setAt(sections, col, minY, y, defaultBlockId);
        }
    }

    /** SurfaceSystem.frozenOceanExtension */
    private void frozenOceanExtension(int[][] sections, int col, int minSurfaceLevel, String biome,
                                      int x, int z, int height, int minY, int seaLevel) {
        double d = Math.min(Math.abs(icebergSurfaceNoise.getValue(x, 0.0, z) * 8.25),
            icebergPillarNoise.getValue(x * 1.28, 0.0, z * 1.28) * 15.0);
        if (d <= 1.8) return;
        double roof = Math.abs(icebergPillarRoofNoise.getValue(x * 1.17, 0.0, z * 1.17) * 1.5);
        double e = Math.min(d * d * 1.2, Math.ceil(roof * 40.0) + 14.0);
        if (BiomeClimate.meltsIcebergSlightly(worldgenData.biomeTemperature(biome),
                worldgenData.biomeFrozenModifier(biome), x, 63, z)) {
            e -= 2.0;
        }
        double g;
        if (e > 2.0) {
            g = seaLevel - e - 7.0;
            e += seaLevel;
        } else {
            e = 0.0;
            g = 0.0;
        }
        double top = e;
        NoiseRandom r = randomFactory.at(x, 0, z);
        int maxSnow = 2 + r.nextInt(4);
        int snowFloor = seaLevel + 18 + r.nextInt(10);
        int snowCount = 0;
        for (int y = Math.max(height, (int) e + 1); y >= minSurfaceLevel; y--) {
            int state = getAt(sections, col, minY, y);
            if ((state == ChunkSection.AIR && y < (int) top && r.nextDouble() > 0.01)
                || (state == waterId && y > (int) g && y < seaLevel && g != 0.0 && r.nextDouble() > 0.15)) {
                if (snowCount <= maxSnow && y > snowFloor) {
                    setAt(sections, col, minY, y, snowBlockId);
                    snowCount++;
                } else {
                    setAt(sections, col, minY, y, packedIceId);
                }
            }
        }
    }

    /** 청크 전체 16×16 surfaceY(WORLD_SURFACE) 스냅샷 — buildSurface·carver regrass가 공유. */
    public int[] surfaceYGrid(ChunkColumn col) {
        int[] grid = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                grid[lz * 16 + lx] = col.surfaceBlockY(lx, lz);
            }
        }
        return grid;
    }

    private int surfaceDepthAt(int worldX, int worldZ) {
        return (int)(surfaceNoise.getValue(worldX, 0, worldZ) * 2.75 + 3.0
            + randomFactory.at(worldX, 0, worldZ).nextDouble() * 0.25);
    }

    /**
     * carver regrass 전용 — vanilla {@code CarvingContext.topMaterial}(SurfaceSystem.topMaterial 단일 포인트
     * 재호출) 이식. carve가 지표를 관통해 노출시킨 dirt를 biome에 맞는 표면 블록으로 되돌릴 때 쓴다.
     * stoneDepthAbove/Below=1, waterY=hasFluid?y+1:MIN_VALUE로 고정하는 것까지 vanilla와 동일
     * (SurfaceSystem.topMaterial의 {@code context1.updateY(1, 1, ...)} 호출과 대응).
     * surfaceYGrid는 carve 이전(buildSurface 직후) 스냅샷을 그대로 넘겨야 한다 — vanilla WORLD_SURFACE_WG도
     * carve 중에는 갱신되지 않는다.
     */
    public Integer topMaterial(int worldX, int y, int worldZ, boolean hasFluid,
                                NoiseChunk nc, int chunkX, int chunkZ, int[] surfaceYGrid) {
        int lx = worldX - chunkX * 16;
        int lz = worldZ - chunkZ * 16;
        int surfaceDepth = surfaceDepthAt(worldX, worldZ);

        int psl00 = nc.getPreliminarySurfaceLevel(chunkX * 4,     chunkZ * 4);
        int psl10 = nc.getPreliminarySurfaceLevel(chunkX * 4 + 4, chunkZ * 4);
        int psl01 = nc.getPreliminarySurfaceLevel(chunkX * 4,     chunkZ * 4 + 4);
        int psl11 = nc.getPreliminarySurfaceLevel(chunkX * 4 + 4, chunkZ * 4 + 4);
        int lerpedPsl = (int) Math.floor(MathHelper.lerp2(
            lx / 16.0, lz / 16.0, psl00, psl10, psl01, psl11));

        SurfaceContext ctx = new SurfaceContext();
        ctx.minY          = settings.minY();
        ctx.maxY          = ctx.minY + settings.height() - 1;
        ctx.seaLevel      = settings.seaLevel();
        ctx.randomFactory = randomFactory;
        ctx.worldgenData  = worldgenData;
        ctx.surfaceYGrid  = surfaceYGrid;
        ctx.biomeCache    = new HashMap<>();
        ctx.surfaceY      = surfaceYGrid[lz * 16 + lx];
        ctx.surfaceDepth  = surfaceDepth;
        ctx.preliminarySurfaceLevel = lerpedPsl + surfaceDepth - 8;
        ctx.blockX = worldX;
        ctx.blockZ = worldZ;
        ctx.blockY = y;
        ctx.stoneDepthAbove = 1;
        ctx.stoneDepthBelow = 1;
        ctx.waterY = hasFluid ? y + 1 : Integer.MIN_VALUE;
        ctx.biomeName = null;

        return eval(rule, ctx);
    }

    private Integer eval(SurfaceRule rule, SurfaceContext ctx) {
        return switch (rule) {
            case SurfaceRule.Sequence seq -> {
                for (SurfaceRule r : seq.rules()) {
                    Integer res = eval(r, ctx);
                    if (res != null) yield res;
                }
                yield null;
            }
            case SurfaceRule.IfTrue ifTrue -> {
                boolean cond = evalCondition(ifTrue.condition(), ctx);
                yield cond ? eval(ifTrue.then(), ctx) : null;
            }
            case SurfaceRule.Block block -> block.blockStateId();
            case SurfaceRule.Bandlands __ -> getBand(ctx.blockX, ctx.blockY, ctx.blockZ);
        };
    }

    private String biomeName(SurfaceContext ctx) {
        if (ctx.biomeName != null) return ctx.biomeName;
        int biomeId = biomeAtBlock(ctx.blockX, ctx.blockY, ctx.blockZ, ctx);
        ctx.biomeName = (biomeId >= 0 && biomeId < registryBiomeNames.length)
            ? registryBiomeNames[biomeId] : "minecraft:plains";
        return ctx.biomeName;
    }

    private int biomeAtBlock(int blockX, int blockY, int blockZ, SurfaceContext ctx) {
        Map<Long, Integer> cache = ctx.biomeCache;
        QuartKey q = biomeManagerQuart(blockX, blockY, blockZ);
        int[] chunkBiomes = ctx.chunkBiomeIds;
        if (chunkBiomes != null) {
            int rx = q.x() - ctx.chunkQuartX0;
            int rz = q.z() - ctx.chunkQuartZ0;
            int ry = q.y() - (settings.minY() >> 2);
            if (rx >= 0 && rx < 4 && rz >= 0 && rz < 4 && ry >= 0 && ry < settings.height() >> 2) {
                return chunkBiomes[(ry * 4 + rz) * 4 + rx];
            }
        }
        long key = packQuart(q.x(), q.y(), q.z());
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        int biomeId = biomeSampler.sampleQuart(q.x(), q.y(), q.z(), router);
        cache.put(key, biomeId);
        return biomeId;
    }

    private static long packQuart(int x, int y, int z) {
        long k = ((long)(x & 0x3FFFFFF) << 38)
            | ((long)(y & 0xFFF) << 26)
            | (long)(z & 0x3FFFFFF);
        // 캐시 키 전용 — 홀수 곱(64비트 전단사)으로 비트를 섞는다. 그대로 쓰면 Long.hashCode 가 몰려 HashMap 버킷이 트리가 됐다.
        return k * 0x9E3779B97F4A7C15L;
    }

    private QuartKey biomeManagerQuart(int blockX, int blockY, int blockZ) {
        int x = blockX - 2;
        int y = blockY - 2;
        int z = blockZ - 2;
        int quartX = x >> 2;
        int quartY = y >> 2;
        int quartZ = z >> 2;
        double fracX = (double)(x & 3) / 4.0;
        double fracY = (double)(y & 3) / 4.0;
        double fracZ = (double)(z & 3) / 4.0;

        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            boolean useX0 = (i & 4) == 0;
            boolean useY0 = (i & 2) == 0;
            boolean useZ0 = (i & 1) == 0;
            int candidateX = useX0 ? quartX : quartX + 1;
            int candidateY = useY0 ? quartY : quartY + 1;
            int candidateZ = useZ0 ? quartZ : quartZ + 1;
            double dx = useX0 ? fracX : fracX - 1.0;
            double dy = useY0 ? fracY : fracY - 1.0;
            double dz = useZ0 ? fracZ : fracZ - 1.0;
            double distance = fiddledDistance(biomeZoomSeed, candidateX, candidateY, candidateZ, dx, dy, dz);
            if (bestDistance > distance) {
                best = i;
                bestDistance = distance;
            }
        }

        return new QuartKey(
            (best & 4) == 0 ? quartX : quartX + 1,
            (best & 2) == 0 ? quartY : quartY + 1,
            (best & 1) == 0 ? quartZ : quartZ + 1
        );
    }

    private static double fiddledDistance(long seed, int x, int y, int z,
                                          double dx, double dy, double dz) {
        long mixed = lcgNext(seed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        mixed = lcgNext(mixed, x);
        mixed = lcgNext(mixed, y);
        mixed = lcgNext(mixed, z);
        double xFiddle = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double yFiddle = fiddle(mixed);
        mixed = lcgNext(mixed, seed);
        double zFiddle = fiddle(mixed);
        return square(dz + zFiddle) + square(dy + yFiddle) + square(dx + xFiddle);
    }

    private static long lcgNext(long seed, long salt) {
        seed *= seed * 6364136223846793005L + 1442695040888963407L;
        return seed + salt;
    }

    private static double fiddle(long seed) {
        double d = (double)Math.floorMod(seed >> 24, 1024) / 1024.0;
        return (d - 0.5) * 0.9;
    }

    private static double square(double v) {
        return v * v;
    }

    private static double map(double value, double fromMin, double fromMax, double toMin, double toMax) {
        return toMin + (value - fromMin) * (toMax - toMin) / (fromMax - fromMin);
    }

    private static long obfuscateSeed(long seed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] input = new byte[Long.BYTES];
            for (int i = 0; i < Long.BYTES; i++) {
                input[i] = (byte)(seed >>> (i * 8));
            }
            byte[] hash = digest.digest(input);
            long result = 0L;
            for (int i = 0; i < Long.BYTES; i++) {
                result |= (hash[i] & 0xFFL) << (i * 8);
            }
            return result;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private record QuartKey(int x, int y, int z) {}

    private boolean evalCondition(SurfaceRule.SurfaceCondition cond, SurfaceContext ctx) {
        return switch (cond) {
            case SurfaceRule.SurfaceCondition.VerticalGradient vg -> evalVerticalGradient(vg, ctx);
            case SurfaceRule.SurfaceCondition.AbovePreliminarySurface __ ->
                ctx.blockY >= ctx.preliminarySurfaceLevel;
            case SurfaceRule.SurfaceCondition.StoneDepth sd -> evalStoneDepth(sd, ctx);
            case SurfaceRule.SurfaceCondition.BiomeIs bi -> bi.biomes().contains(biomeName(ctx));
            case SurfaceRule.SurfaceCondition.YAbove ya -> evalYAbove(ya, ctx);
            case SurfaceRule.SurfaceCondition.Water w  -> evalWater(w, ctx);
            case SurfaceRule.SurfaceCondition.NoiseThreshold nt -> evalNoiseThreshold(nt, ctx);
            case SurfaceRule.SurfaceCondition.Temperature __ -> {
                String biome = biomeName(ctx);
                yield BiomeClimate.coldEnoughToSnow(worldgenData.biomeTemperature(biome),
                    worldgenData.biomeFrozenModifier(biome), ctx.blockX, ctx.blockY, ctx.blockZ);
            }
            case SurfaceRule.SurfaceCondition.Steep __ -> evalSteep(ctx);
            case SurfaceRule.SurfaceCondition.Not n -> !evalCondition(n.inner(), ctx);
            case SurfaceRule.SurfaceCondition.Hole __ -> ctx.surfaceDepth <= 0;
        };
    }

    private boolean evalVerticalGradient(SurfaceRule.SurfaceCondition.VerticalGradient vg,
                                          SurfaceContext ctx) {
        int trueY  = resolveY(vg.trueAtAndBelow(), ctx);
        int falseY = resolveY(vg.falseAtAndAbove(), ctx);
        if (ctx.blockY <= trueY)  return true;
        if (ctx.blockY >= falseY) return false;
        double gradient = (double)(ctx.blockY - trueY) / (double)(falseY - trueY);
        NoiseRandom rng = gradientFactoryCache
            .computeIfAbsent(vg.randomName(), name -> randomFactory.fromHashOf(name).forkPositional())
            .at(ctx.blockX, ctx.blockY, ctx.blockZ);
        // 바닐라: (double) nextFloat() < map(y, trueY, falseY, 1.0, 0.0)
        return (double) rng.nextFloat() < (1.0 - gradient);
    }

    private boolean evalStoneDepth(SurfaceRule.SurfaceCondition.StoneDepth sd, SurfaceContext ctx) {
        int depth = switch (sd.surfaceType()) {
            case FLOOR   -> ctx.stoneDepthAbove;
            case CEILING -> ctx.stoneDepthBelow;
        };
        int secondaryDepth = sd.secondaryDepthRange() == 0 ? 0
            : (int) map(surfaceSecondaryNoise.getValue(ctx.blockX, 0, ctx.blockZ),
                -1.0, 1.0, 0.0, sd.secondaryDepthRange());
        int maxDepth = sd.offset() + 1 + secondaryDepth;
        if (sd.addSurfaceDepth()) maxDepth += ctx.surfaceDepth;
        return depth <= maxDepth;
    }

    private boolean evalYAbove(SurfaceRule.SurfaceCondition.YAbove ya, SurfaceContext ctx) {
        // 바닐라 y_above: blockY + (add_stone_depth ? stoneDepthAbove : 0) >= anchor + surfaceDepth * multiplier
        int y = resolveY(ya.anchor(), ctx);
        return ctx.blockY + (ya.addStoneDepth() ? ctx.stoneDepthAbove : 0) >= y + ya.surfaceDepthMultiplier() * ctx.surfaceDepth;
    }

    private boolean evalWater(SurfaceRule.SurfaceCondition.Water w, SurfaceContext ctx) {
        // ctx.waterY == Integer.MIN_VALUE means no fluid found above → always above water
        if (ctx.waterY == Integer.MIN_VALUE) return true;
        int addDepth = w.addStoneDepth() ? ctx.stoneDepthAbove : 0;
        return ctx.blockY + addDepth >= ctx.waterY + w.offset()
            + w.surfaceDepthMultiplier() * ctx.surfaceDepth;
    }

    private boolean evalNoiseThreshold(SurfaceRule.SurfaceCondition.NoiseThreshold nt,
                                        SurfaceContext ctx) {
        NormalNoise noise = buildNoise(nt.noiseId());
        double v = noise.getValue(ctx.blockX, 0, ctx.blockZ);
        return v >= nt.minInclusive() && v <= nt.maxExclusive();
    }

    /** 바닐라 SteepMaterialCondition — 청크 안 WORLD_SURFACE_WG 높이 기준(경계는 청크 가장자리로 clamp). */
    private boolean evalSteep(SurfaceContext ctx) {
        int lx = ctx.blockX & 15;
        int lz = ctx.blockZ & 15;
        int[] g = ctx.surfaceYGrid;
        int zn = Math.max(lz - 1, 0);
        int zp = Math.min(lz + 1, 15);
        if (g[zp * 16 + lx] >= g[zn * 16 + lx] + 4) return true;
        int xn = Math.max(lx - 1, 0);
        int xp = Math.min(lx + 1, 15);
        return g[lz * 16 + xn] >= g[lz * 16 + xp] + 4;
    }

    private int resolveY(SurfaceRule.YOffset offset, SurfaceContext ctx) {
        return switch (offset) {
            case SurfaceRule.YOffset.Absolute a    -> a.y();
            case SurfaceRule.YOffset.AboveBottom ab -> ctx.minY + ab.offset();
            case SurfaceRule.YOffset.BelowTop bt    -> ctx.maxY - bt.offset();
        };
    }
}
