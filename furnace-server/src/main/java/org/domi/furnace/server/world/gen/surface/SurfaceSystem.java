// 표층 규칙을 noise fill 이후 ChunkColumn에 적용하는 오케스트레이터
package org.domi.furnace.server.world.gen.surface;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
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
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory randomFactory;
    private final Map<String, NormalNoise> noiseCache = new ConcurrentHashMap<>();
    // vertical_gradient용 positional factory 캐시 — fromHashOf는 매 호출 MD5 다이제스트를 수행하는
    // randomName만의 순수 함수라 캐시해도 RNG 스트림 불변 (M-opt P0, buildNoise의 noiseCache와 동일 패턴).
    private final Map<String, XoroshiroRandomSource.XoroshiroPositionalRandomFactory> gradientFactoryCache = new ConcurrentHashMap<>();
    private final NormalNoise surfaceNoise;
    private final NormalNoise surfaceSecondaryNoise;
    private final int waterId;
    private final int defaultBlockId;
    private final String[] registryBiomeNames;
    private final BiomeSampler biomeSampler;
    private final NoiseRouter router;
    private final long biomeZoomSeed;

    private SurfaceSystem(SurfaceRule rule, NoiseSettings settings,
                          WorldgenData worldgenData, int waterId, int defaultBlockId,
                          long seed, String[] registryBiomeNames,
                          BiomeSampler biomeSampler, NoiseRouter router) {
        this.rule                = rule;
        this.settings            = settings;
        this.worldgenData        = worldgenData;
        this.waterId             = waterId;
        this.defaultBlockId      = defaultBlockId;
        this.randomFactory       = new XoroshiroRandomSource(seed).forkPositional();
        this.surfaceNoise        = buildNoise("minecraft:surface");
        this.surfaceSecondaryNoise = buildNoise("minecraft:surface_secondary");
        this.registryBiomeNames  = registryBiomeNames;
        this.biomeSampler        = biomeSampler;
        this.router              = router;
        this.biomeZoomSeed       = obfuscateSeed(seed);
    }

    public static SurfaceSystem create(WorldgenData data, NoiseSettings settings,
                                       BlockRegistry reg, long seed,
                                       String[] registryBiomeNames,
                                       BiomeSampler biomeSampler, NoiseRouter router) {
        var noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = new SurfaceRuleParser(reg).parse(
            noiseSettingsJson.get("surface_rule").getAsJsonObject());
        int waterId       = reg.defaultStateId(settings.defaultFluid());
        int defaultBlockId = reg.defaultStateId(settings.defaultBlock());
        return new SurfaceSystem(rule, settings, data, waterId, defaultBlockId, seed,
            registryBiomeNames, biomeSampler, router);
    }

    private NormalNoise buildNoise(String noiseId) {
        return noiseCache.computeIfAbsent(noiseId, id -> {
            XoroshiroRandomSource rng = randomFactory.fromHashOf(id);
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
        int[][] sectionBlocks = new int[ChunkColumn.SECTION_COUNT][];
        for (int i = 0; i < ChunkColumn.SECTION_COUNT; i++) {
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

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = chunkX * 16 + lx;
                int worldZ = chunkZ * 16 + lz;

                int surfaceY = surfaceYGrid[lz * 16 + lx];
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

                // stoneBottomY: cached bottom of current solid column for stoneDepthBelow.
                int stoneBottomY = Integer.MAX_VALUE;
                int columnIndex = lz * 16 + lx;
                int sectionIndex = ChunkColumn.SECTION_COUNT - 1;
                int localY = 15;
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
            case SurfaceRule.Bandlands __ -> null;
        };
    }

    private String biomeName(SurfaceContext ctx) {
        if (ctx.biomeName != null) return ctx.biomeName;
        int biomeId = biomeAtBlock(ctx.blockX, ctx.blockY, ctx.blockZ, ctx.biomeCache);
        ctx.biomeName = (biomeId >= 0 && biomeId < registryBiomeNames.length)
            ? registryBiomeNames[biomeId] : "minecraft:plains";
        return ctx.biomeName;
    }

    private int biomeAtBlock(int blockX, int blockY, int blockZ, Map<Long, Integer> cache) {
        QuartKey q = biomeManagerQuart(blockX, blockY, blockZ);
        long key = packQuart(q.x(), q.y(), q.z());
        Integer cached = cache.get(key);
        if (cached != null) return cached;
        int biomeId = biomeSampler.sampleQuart(q.x(), q.y(), q.z(), router);
        cache.put(key, biomeId);
        return biomeId;
    }

    private static long packQuart(int x, int y, int z) {
        return ((long)(x & 0x3FFFFFF) << 38)
            | ((long)(y & 0xFFF) << 26)
            | (long)(z & 0x3FFFFFF);
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
            case SurfaceRule.SurfaceCondition.Temperature __ ->
                ctx.worldgenData.biomeTemperature(ctx.biomeName) < 0.15f;
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
        XoroshiroRandomSource rng = gradientFactoryCache
            .computeIfAbsent(vg.randomName(), name -> randomFactory.fromHashOf(name).forkPositional())
            .at(ctx.blockX, ctx.blockY, ctx.blockZ);
        return rng.nextFloat() < (1.0f - (float)gradient);
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
        int y = resolveY(ya.anchor(), ctx);
        if (ya.addStoneDepth()) y += ctx.stoneDepthAbove;
        return ctx.blockY >= y + ya.surfaceDepthMultiplier() * ctx.surfaceDepth;
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

    private boolean evalSteep(SurfaceContext ctx) {
        int lx = ctx.blockX & 15;
        int lz = ctx.blockZ & 15;
        int sy = ctx.surfaceY;
        if (lx > 0  && Math.abs(sy - ctx.surfaceYGrid[lz * 16 + (lx - 1)]) > 4) return true;
        if (lx < 15 && Math.abs(sy - ctx.surfaceYGrid[lz * 16 + (lx + 1)]) > 4) return true;
        if (lz > 0  && Math.abs(sy - ctx.surfaceYGrid[(lz - 1) * 16 + lx])  > 4) return true;
        if (lz < 15 && Math.abs(sy - ctx.surfaceYGrid[(lz + 1) * 16 + lx])  > 4) return true;
        return false;
    }

    private int resolveY(SurfaceRule.YOffset offset, SurfaceContext ctx) {
        return switch (offset) {
            case SurfaceRule.YOffset.Absolute a    -> a.y();
            case SurfaceRule.YOffset.AboveBottom ab -> ctx.minY + ab.offset();
            case SurfaceRule.YOffset.BelowTop bt    -> ctx.maxY - bt.offset();
        };
    }
}
