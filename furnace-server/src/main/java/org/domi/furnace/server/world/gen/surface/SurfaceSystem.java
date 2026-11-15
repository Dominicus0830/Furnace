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
import org.domi.furnace.server.world.BiomeZoom;
import org.domi.furnace.server.world.BlockRegistry;

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
        this.biomeZoomSeed       = BiomeZoom.obfuscateSeed(seed);
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
        this.interpret = Boolean.getBoolean("furnace.surfaceInterp");
        this.zoomTable = !"false".equals(System.getProperty("furnace.surface.zoomTable"));
        this.compiled = compile(rule);
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
        initBiomeContext(ctx, biomeIds, chunkX, chunkZ, zoomTable);
        initCompiledCaches(ctx);

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
                ctx.column++;
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
                            int result = applyRule(ctx);
                            if (result >= 0) blocks[blockIndex] = result;
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
        initCompiledCaches(ctx);
        ctx.column = 1;
        int r = applyRule(ctx);
        return r >= 0 ? r : null;
    }

    /** 이 블록에 규칙을 적용한 결과 상태(없으면 -1). */
    private int applyRule(SurfaceContext ctx) {
        ctx.biomeName = null;
        ctx.biomeIdValid = false;
        if (interpret) {
            Integer r = eval(rule, ctx);
            return r != null ? r : -1;
        }
        return compiled.apply(ctx);
    }

    // ================================================================== 컴파일된 표면 규칙
    //
    // 규칙 트리(SurfaceRule 레코드)를 블록마다 해석하던 것을, 시작할 때 한 번 노드 객체로 바꿔 둔다(바닐라 SurfaceRules 의
    // RuleSource → SurfaceRule 람다와 같은 생각). 결과는 해석기와 같다:
    //  - (x, 0, z) 에서만 읽는 값(노이즈 임계값·보조 깊이 노이즈·점토 밴드 오프셋)은 기둥마다 한 번 구한다(바닐라 LazyXZCondition).
    //  - 바이옴 조건은 바이옴 id 표로, 온도는 id 별로 미리 읽은 값으로 본다(이름 문자열·맵 조회 없음).
    //  - y 기준점(above_bottom·below_top)과 vertical_gradient 의 난수 팩토리는 미리 풀어 둔다.
    // 캐시는 노드가 아니라 SurfaceContext 에 둔다(여러 생성 스레드가 같은 컴파일 결과를 쓴다).
    // -Dfurnace.surfaceInterp=true 면 예전 해석기를 쓴다(대조용).

    private final boolean interpret;
    private final CRule compiled;
    private static final int SLOT_SECONDARY = 0, SLOT_BAND = 1;
    private int xzSlots = 2;
    private final Map<String, Integer> noiseSlots = new java.util.HashMap<>();

    private void initCompiledCaches(SurfaceContext ctx) {
        ctx.xzValue = new double[xzSlots];
        ctx.xzColumn = new int[xzSlots];
        java.util.Arrays.fill(ctx.xzColumn, Integer.MIN_VALUE);
        ctx.column = 0;
    }

    private abstract static class CRule { abstract int apply(SurfaceContext c); }
    private abstract static class CCond { abstract boolean test(SurfaceContext c); }

    private int biomeIdOf(SurfaceContext c) {
        if (!c.biomeIdValid) {
            c.biomeId = biomeAtBlock(c.blockX, c.blockY, c.blockZ, c);
            c.biomeIdValid = true;
        }
        return c.biomeId;
    }

    private double secondaryAt(SurfaceContext c) {
        if (c.xzColumn[SLOT_SECONDARY] != c.column) {
            c.xzValue[SLOT_SECONDARY] = surfaceSecondaryNoise.getValue(c.blockX, 0, c.blockZ);
            c.xzColumn[SLOT_SECONDARY] = c.column;
        }
        return c.xzValue[SLOT_SECONDARY];
    }

    private int bandAt(SurfaceContext c) {
        if (c.xzColumn[SLOT_BAND] != c.column) {
            c.xzValue[SLOT_BAND] = (int) Math.round(clayBandsOffsetNoise.getValue(c.blockX, 0.0, c.blockZ) * 4.0);
            c.xzColumn[SLOT_BAND] = c.column;
        }
        int offset = (int) c.xzValue[SLOT_BAND];
        return clayBands[(c.blockY + offset + clayBands.length) % clayBands.length];
    }

    private CRule compile(SurfaceRule r) {
        return switch (r) {
            case SurfaceRule.Sequence seq -> {
                CRule[] parts = seq.rules().stream().map(this::compile).toArray(CRule[]::new);
                yield new CRule() {
                    int apply(SurfaceContext c) {
                        for (CRule p : parts) {
                            int v = p.apply(c);
                            if (v >= 0) return v;
                        }
                        return -1;
                    }
                };
            }
            case SurfaceRule.IfTrue it -> {
                CCond cond = compile(it.condition());
                CRule then = compile(it.then());
                yield new CRule() {
                    int apply(SurfaceContext c) { return cond.test(c) ? then.apply(c) : -1; }
                };
            }
            case SurfaceRule.Block b -> {
                int state = b.blockStateId();
                yield new CRule() { int apply(SurfaceContext c) { return state; } };
            }
            case SurfaceRule.Bandlands __ -> new CRule() { int apply(SurfaceContext c) { return bandAt(c); } };
        };
    }

    private int resolveConstY(SurfaceRule.YOffset offset) {
        int minY = settings.minY(), maxY = minY + settings.height() - 1;
        return switch (offset) {
            case SurfaceRule.YOffset.Absolute a -> a.y();
            case SurfaceRule.YOffset.AboveBottom ab -> minY + ab.offset();
            case SurfaceRule.YOffset.BelowTop bt -> maxY - bt.offset();
        };
    }

    private CCond compile(SurfaceRule.SurfaceCondition cond) {
        return switch (cond) {
            case SurfaceRule.SurfaceCondition.VerticalGradient vg -> {
                int trueY = resolveConstY(vg.trueAtAndBelow()), falseY = resolveConstY(vg.falseAtAndAbove());
                PositionalRandom factory = gradientFactoryCache.computeIfAbsent(vg.randomName(),
                    name -> randomFactory.fromHashOf(name).forkPositional());
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        if (c.blockY <= trueY) return true;
                        if (c.blockY >= falseY) return false;
                        double gradient = (double) (c.blockY - trueY) / (double) (falseY - trueY);
                        return (double) factory.at(c.blockX, c.blockY, c.blockZ).nextFloat() < (1.0 - gradient);
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.AbovePreliminarySurface __ -> new CCond() {
                boolean test(SurfaceContext c) { return c.blockY >= c.preliminarySurfaceLevel; }
            };
            case SurfaceRule.SurfaceCondition.StoneDepth sd -> {
                boolean floor = sd.surfaceType() == SurfaceRule.SurfaceType.FLOOR;
                int range = sd.secondaryDepthRange(), offset = sd.offset();
                boolean addSurface = sd.addSurfaceDepth();
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        int depth = floor ? c.stoneDepthAbove : c.stoneDepthBelow;
                        int secondary = range == 0 ? 0 : (int) map(secondaryAt(c), -1.0, 1.0, 0.0, range);
                        int maxDepth = offset + 1 + secondary;
                        if (addSurface) maxDepth += c.surfaceDepth;
                        return depth <= maxDepth;
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.BiomeIs bi -> {
                boolean[] match = new boolean[registryBiomeNames.length];
                for (int i = 0; i < match.length; i++) match[i] = bi.biomes().contains(registryBiomeNames[i]);
                boolean fallback = bi.biomes().contains("minecraft:plains");
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        int id = biomeIdOf(c);
                        return id >= 0 && id < match.length ? match[id] : fallback;
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.YAbove ya -> {
                int anchor = resolveConstY(ya.anchor());
                int mult = ya.surfaceDepthMultiplier();
                boolean addStone = ya.addStoneDepth();
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        return c.blockY + (addStone ? c.stoneDepthAbove : 0) >= anchor + mult * c.surfaceDepth;
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.Water w -> {
                int offset = w.offset(), mult = w.surfaceDepthMultiplier();
                boolean addStone = w.addStoneDepth();
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        if (c.waterY == Integer.MIN_VALUE) return true;
                        int add = addStone ? c.stoneDepthAbove : 0;
                        return c.blockY + add >= c.waterY + offset + mult * c.surfaceDepth;
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.NoiseThreshold nt -> {
                NormalNoise noise = buildNoise(nt.noiseId());
                int slot = noiseSlots.computeIfAbsent(nt.noiseId(), k -> xzSlots++);
                double min = nt.minInclusive(), max = nt.maxExclusive();
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        if (c.xzColumn[slot] != c.column) {
                            c.xzValue[slot] = noise.getValue(c.blockX, 0, c.blockZ);
                            c.xzColumn[slot] = c.column;
                        }
                        double v = c.xzValue[slot];
                        return v >= min && v <= max;
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.Temperature __ -> {
                float[] temp = new float[registryBiomeNames.length];
                boolean[] frozen = new boolean[registryBiomeNames.length];
                for (int i = 0; i < temp.length; i++) {
                    temp[i] = worldgenData.biomeTemperature(registryBiomeNames[i]);
                    frozen[i] = worldgenData.biomeFrozenModifier(registryBiomeNames[i]);
                }
                float fbTemp = worldgenData.biomeTemperature("minecraft:plains");
                boolean fbFrozen = worldgenData.biomeFrozenModifier("minecraft:plains");
                yield new CCond() {
                    boolean test(SurfaceContext c) {
                        int id = biomeIdOf(c);
                        boolean in = id >= 0 && id < temp.length;
                        return BiomeClimate.coldEnoughToSnow(in ? temp[id] : fbTemp, in ? frozen[id] : fbFrozen,
                            c.blockX, c.blockY, c.blockZ);
                    }
                };
            }
            case SurfaceRule.SurfaceCondition.Steep __ -> new CCond() {
                boolean test(SurfaceContext c) { return evalSteep(c); }
            };
            case SurfaceRule.SurfaceCondition.Not n -> {
                CCond inner = compile(n.inner());
                yield new CCond() { boolean test(SurfaceContext c) { return !inner.test(c); } };
            }
            case SurfaceRule.SurfaceCondition.Hole __ -> new CCond() {
                boolean test(SurfaceContext c) { return c.surfaceDepth <= 0; }
            };
        };
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
        if (ctx.zoomFiddles != null) {
            int v = biomeAtBlockTable(blockX, blockY, blockZ, ctx);
            if (v != Integer.MIN_VALUE) return v;
        }
        QuartKey q = biomeManagerQuart(blockX, blockY, blockZ);
        int[] halo = ctx.haloBiomes;
        if (halo != null) {
            // buildSurface: 바닐라 WorldGenRegion 바이옴(이웃 청크 저장 바이옴, y 자르기)
            int hx = q.x() - (ctx.chunkQuartX0 - 1);
            int hz = q.z() - (ctx.chunkQuartZ0 - 1);
            int hy = q.y() - ctx.haloQuartY0;
            if (hx >= 0 && hx < 6 && hz >= 0 && hz < 6 && hy >= 0 && hy < ctx.haloQuartYCount) {
                return zoomBiome(ctx, (hy * 6 + hz) * 6 + hx, q.x(), q.y(), q.z());
            }
            return storedBiome(ctx, q.x(), q.y(), q.z());
        }
        // carver 의 topMaterial: 바닐라도 biomeSource 를 바로 읽는다(BiomeManager.withDifferentSource) — 자르지 않는다
        Map<Long, Integer> cache = ctx.biomeCache;
        long key = packQuart(q.x(), q.y(), q.z());
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        int biomeId = biomeSampler.sampleQuart(q.x(), q.y(), q.z(), router);
        cache.put(key, biomeId);
        return biomeId;
    }

    // ------------------------------------------------------------------ 표면 규칙의 꼭짓점 바이옴 출처
    //
    // 바닐라 buildSurface 는 WorldGenRegion 의 BiomeManager 를 쓴다. ChunkPyramid SURFACE 단계가 반경 1 이웃을 BIOMES 이상으로 요구하므로
    // 꼭짓점 quart 의 바이옴은 그 quart 가 속한 청크(자기 또는 이웃)에 **저장된** 바이옴이고, ChunkAccess.getNoiseBiome 이 quart y 를
    // 차원 높이 범위로 자른다. Furnace 의 저장 바이옴 = BiomeSampler.sampleChunk 결과(직전 결과 R-트리 힌트 순서).
    // 이웃 배열이 캐시에 있으면 그대로 읽고, 없으면 그 quart 만 검색한다 — 기후 값은 sampleChunk 와 같고(fillTarget = 직접 계산) 힌트는 거리 동률일
    // 때만 답을 바꾸므로, 동률이 없으면 같은 답이다. 동률이면 그 이웃 청크의 sampleChunk 를 통째로 만든다(드물다).

    /** 청크 저장 바이옴 공급자(NoiseWorldSource 가 건다). 배열은 sampleChunk 결과, 인덱스 ((qy-차원 최저 quart)*4+qz)*4+qx, 읽기 전용. */
    public interface ChunkBiomes {
        /** 이미 만든 그 청크의 바이옴 배열, 없으면 null. */
        int[] cached(int chunkX, int chunkZ);
        /** 그 청크의 바이옴 배열을 만든다(동률일 때만 부른다). */
        int[] compute(int chunkX, int chunkZ);
    }

    private volatile ChunkBiomes chunkBiomes;
    private volatile int dimMinQuartY;
    private volatile int dimQuartYCount;

    /** 차원 높이(바이옴 배열 범위)와 이웃 청크 바이옴 공급자를 건다. 걸지 않으면 노이즈 생성 높이를 차원으로 본다. */
    public void attachChunkBiomes(ChunkBiomes source, int dimensionMinY, int dimensionHeight) {
        this.dimMinQuartY = dimensionMinY >> 2;
        this.dimQuartYCount = dimensionHeight >> 2;
        this.chunkBiomes = source;
    }

    /** buildSurface 용 바이옴 문맥: 꼭짓점 표(6×6×높이)를 만들고, 바이옴 배열을 아는 청크(자기·캐시된 이웃)의 칸은 미리 채운다. */
    private void initBiomeContext(SurfaceContext ctx, int[] biomeIds, int chunkX, int chunkZ, boolean table) {
        int minY = settings.minY(), maxY = minY + settings.height() - 1;
        ctx.biomeCache = new HashMap<>();
        ctx.chunkBiomeIds = biomeIds;
        ctx.chunkQuartX0 = chunkX * 4;
        ctx.chunkQuartZ0 = chunkZ * 4;
        // biomeManagerQuart 는 블록 y-2 의 quart 와 그 위 하나를 고른다 — 생성 높이 quart 범위 한 칸 아래·위까지 덮는다
        ctx.haloQuartY0 = (minY >> 2) - 1;
        ctx.haloQuartYCount = ((maxY >> 2) + 1) - ctx.haloQuartY0 + 1;
        int[] halo = new int[6 * 6 * ctx.haloQuartYCount];
        java.util.Arrays.fill(halo, Integer.MIN_VALUE);
        ctx.haloBiomes = halo;
        int dq0 = dimQuartY0(), dqN = dimQuartYs();
        int[][] arrays = new int[9][];
        ChunkBiomes cb = chunkBiomes;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int[] a = dx == 0 && dz == 0 ? biomeIds : cb != null ? cb.cached(chunkX + dx, chunkZ + dz) : null;
                arrays[(dz + 1) * 3 + dx + 1] = a != null && a.length == dqN * 16 ? a : null;
            }
        }
        ctx.neighborBiomes = arrays;
        // 아는 배열에서 미리 채운다(모든 꼭짓점이 알려져 있으면 같은 바이옴 생략이 더 자주 걸린다)
        for (int hz = 0; hz < 6; hz++) {
            int qz = ctx.chunkQuartZ0 - 1 + hz;
            for (int hx = 0; hx < 6; hx++) {
                int qx = ctx.chunkQuartX0 - 1 + hx;
                int[] a = arrays[((qz >> 2) - chunkZ + 1) * 3 + (qx >> 2) - chunkX + 1];
                if (a == null) continue;
                for (int hy = 0; hy < ctx.haloQuartYCount; hy++) {
                    int qyc = Math.max(dq0, Math.min(dq0 + dqN - 1, ctx.haloQuartY0 + hy));
                    halo[(hy * 6 + hz) * 6 + hx] = a[((qyc - dq0) * 4 + (qz & 3)) * 4 + (qx & 3)];
                }
            }
        }
        if (table) {
            double[] f = new double[halo.length * 3];
            java.util.Arrays.fill(f, Double.NaN);
            ctx.zoomFiddles = f;
        }
    }

    private int dimQuartY0() { return dimQuartYCount > 0 ? dimMinQuartY : settings.minY() >> 2; }

    private int dimQuartYs() { return dimQuartYCount > 0 ? dimQuartYCount : settings.height() >> 2; }

    /** 꼭짓점 표 칸 hi(= quart qx,qy,qz)의 바이옴 — 모르면 구해서 메모. */
    private int zoomBiome(SurfaceContext ctx, int hi, int qx, int qy, int qz) {
        int v = ctx.haloBiomes[hi];
        if (v == Integer.MIN_VALUE) {
            v = storedBiome(ctx, qx, qy, qz);
            ctx.haloBiomes[hi] = v;
        }
        return v;
    }

    /** 바닐라 WorldGenRegion.getNoiseBiome(qx, qy, qz): quart 가 속한 청크의 저장 바이옴, y 는 차원 범위로 자른다. */
    private int storedBiome(SurfaceContext ctx, int qx, int qy, int qz) {
        int dq0 = dimQuartY0(), dqN = dimQuartYs();
        int qyc = Math.max(dq0, Math.min(dq0 + dqN - 1, qy));
        int ncx = qx >> 2, ncz = qz >> 2;
        int dx = ncx - (ctx.chunkQuartX0 >> 2), dz = ncz - (ctx.chunkQuartZ0 >> 2);
        int ni = dx >= -1 && dx <= 1 && dz >= -1 && dz <= 1 ? (dz + 1) * 3 + dx + 1 : -1;
        int idx = ((qyc - dq0) * 4 + (qz & 3)) * 4 + (qx & 3);
        int[] a = ni >= 0 && ctx.neighborBiomes != null ? ctx.neighborBiomes[ni] : null;
        if (a != null) return a[idx];
        int v = biomeSampler.sampleQuartUnique(qx, qyc, qz, router);
        if (v != Integer.MIN_VALUE) return v;
        // 거리 동률 — 그 청크 sampleChunk 의 힌트 순서가 답을 정한다
        ChunkBiomes cb = chunkBiomes;
        if (cb == null) return biomeSampler.sampleQuart(qx, qyc, qz, router);
        a = cb.compute(ncx, ncz);
        if (ni >= 0 && ctx.neighborBiomes != null) ctx.neighborBiomes[ni] = a;
        return a[idx];
    }

    // ------------------------------------------------------------------ 바이옴 확대(지터) 표
    //
    // 바닐라 BiomeManager.getBiome 은 블록마다 둘레 quart 꼭짓점 8개의 지터 거리(꼭짓점마다 LCG 9번)를 다시 구한다.
    // 표면 규칙은 거의 모든 돌 블록에서 바이옴을 물으므로(네더는 새 청크 CPU 의 약 60%) 청크 단위로 두 가지를 메모한다.
    //  1) 꼭짓점 8개의 바이옴이 모두 같으면 어느 꼭짓점이 이기든 답이 같다 — 거리 계산을 건너뛴다.
    //  2) 꼭짓점 지터(fiddle 3개)는 시드·quart 좌표만의 함수다 — 꼭짓점마다 한 번만 구한다(같은 식, 같은 덧셈 순서).
    // 꼭짓점 바이옴은 haloBiomes 한 표(6×6×높이)에 둔다(출처는 위 storedBiome).
    // -Dfurnace.surface.zoomTable=false 면 블록마다 지터를 계산한다(대조용, 꼭짓점 바이옴 출처는 같다).

    private final boolean zoomTable;
    /** 꼭짓점 번호 i(바닐라 순서: x=4, y=2, z=1 비트) → haloBiomes 안 상대 위치. x +1, z +6, y +36. */
    private static final int[] ZOOM_OFFSET = {0, 6, 36, 42, 1, 7, 37, 43};

    /** 표 범위 안이면 바닐라 getBiome 과 같은 답, 밖이면 Integer.MIN_VALUE(일반 경로로). */
    private int biomeAtBlockTable(int blockX, int blockY, int blockZ, SurfaceContext ctx) {
        int x = blockX - 2;
        int y = blockY - 2;
        int z = blockZ - 2;
        int qx = x >> 2, qy = y >> 2, qz = z >> 2;
        int hx = qx - (ctx.chunkQuartX0 - 1);
        int hz = qz - (ctx.chunkQuartZ0 - 1);
        int hy = qy - ctx.haloQuartY0;
        if (hx < 0 || hx > 4 || hz < 0 || hz > 4 || hy < 0 || hy + 1 >= ctx.haloQuartYCount) return Integer.MIN_VALUE;
        int[] b = ctx.haloBiomes;
        int i0 = (hy * 6 + hz) * 6 + hx;
        int v = b[i0];
        if (v != Integer.MIN_VALUE && b[i0 + 1] == v && b[i0 + 6] == v && b[i0 + 7] == v
                && b[i0 + 36] == v && b[i0 + 37] == v && b[i0 + 42] == v && b[i0 + 43] == v) {
            return v;
        }

        double fracX = (double)(x & 3) / 4.0;
        double fracY = (double)(y & 3) / 4.0;
        double fracZ = (double)(z & 3) / 4.0;
        double[] f = ctx.zoomFiddles;
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < 8; i++) {
            boolean useX0 = (i & 4) == 0;
            boolean useY0 = (i & 2) == 0;
            boolean useZ0 = (i & 1) == 0;
            int fi = (i0 + ZOOM_OFFSET[i]) * 3;
            if (Double.isNaN(f[fi])) {
                fillFiddles(biomeZoomSeed, useX0 ? qx : qx + 1, useY0 ? qy : qy + 1, useZ0 ? qz : qz + 1, f, fi);
            }
            double dx = useX0 ? fracX : fracX - 1.0;
            double dy = useY0 ? fracY : fracY - 1.0;
            double dz = useZ0 ? fracZ : fracZ - 1.0;
            // fiddledDistance 와 같은 식·같은 덧셈 순서(z, y, x)
            double distance = square(dz + f[fi + 2]) + square(dy + f[fi + 1]) + square(dx + f[fi]);
            if (bestDistance > distance) {
                best = i;
                bestDistance = distance;
            }
        }
        return zoomBiome(ctx, i0 + ZOOM_OFFSET[best], (best & 4) == 0 ? qx : qx + 1, (best & 2) == 0 ? qy : qy + 1,
            (best & 1) == 0 ? qz : qz + 1);
    }

    /**
     * 시험용: buildSurface 와 같은 문맥으로 청크 안 모든 블록(생성 높이)의 표면 규칙 바이옴을 [y][z][x] 순으로 돌려준다.
     * table=false 면 꼭짓점 표 없이 블록마다 지터를 계산한다(꼭짓점 바이옴 출처는 같다).
     */
    int[] blockBiomesForTest(int[] biomeIds, int chunkX, int chunkZ, boolean table) {
        int minY = settings.minY(), maxY = minY + settings.height() - 1;
        SurfaceContext ctx = new SurfaceContext();
        initBiomeContext(ctx, biomeIds, chunkX, chunkZ, table);
        int[] out = new int[(maxY - minY + 1) * 256];
        int i = 0;
        for (int y = minY; y <= maxY; y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++)
                    out[i++] = biomeAtBlock(chunkX * 16 + x, y, chunkZ * 16 + z, ctx);
        return out;
    }

    /** fiddledDistance 의 꼭짓점 지터 3개(x, y, z)를 out[off..off+2] 에. */
    private static void fillFiddles(long seed, int x, int y, int z, double[] out, int off) {
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
        out[off + 1] = yFiddle;
        out[off + 2] = zFiddle;
        out[off] = xFiddle;
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
