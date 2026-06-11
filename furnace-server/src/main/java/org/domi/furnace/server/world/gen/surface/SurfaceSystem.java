// 표층 규칙을 noise fill 이후 ChunkColumn에 적용하는 오케스트레이터
package org.domi.furnace.server.world.gen.surface;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.NoiseChunk;
import org.domi.furnace.server.world.gen.NoiseSettings;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.domi.furnace.server.world.gen.noise.NormalNoise;
import org.domi.furnace.server.world.gen.noise.XoroshiroRandomSource;
import org.domi.furnace.server.world.BlockRegistry;

import java.util.HashMap;
import java.util.Map;

public final class SurfaceSystem {

    private final SurfaceRule rule;
    private final NoiseSettings settings;
    private final WorldgenData worldgenData;
    private final XoroshiroRandomSource.XoroshiroPositionalRandomFactory randomFactory;
    private final Map<String, NormalNoise> noiseCache = new HashMap<>();
    private final NormalNoise surfaceNoise;
    private final int waterId;

    private SurfaceSystem(SurfaceRule rule, NoiseSettings settings,
                          WorldgenData worldgenData, int waterId, long seed) {
        this.rule          = rule;
        this.settings      = settings;
        this.worldgenData  = worldgenData;
        this.waterId       = waterId;
        this.randomFactory = new XoroshiroRandomSource(seed).forkPositional();
        this.surfaceNoise  = buildNoise("minecraft:surface");
    }

    public static SurfaceSystem create(WorldgenData data, NoiseSettings settings,
                                       BlockRegistry reg, long seed) {
        var noiseSettingsJson = data.noiseSettings("minecraft:overworld");
        SurfaceRule rule = new SurfaceRuleParser(reg).parse(
            noiseSettingsJson.get("surface_rule").getAsJsonObject());
        int waterId = reg.defaultStateId(settings.defaultFluid());
        return new SurfaceSystem(rule, settings, data, waterId, seed);
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

    public void buildSurface(ChunkColumn col, int[] biomeIds, String[] biomeNames,
                             NoiseChunk nc, int chunkX, int chunkZ) {
        int minY     = settings.minY();
        int maxY     = minY + settings.height() - 1;
        int seaLevel = settings.seaLevel();

        // pre-build surfaceY grid [lz*16+lx]
        int[] surfaceYGrid = new int[256];
        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int sy = minY - 1;
                for (int y = maxY; y >= minY; y--) {
                    if (col.getBlock(lx, y, lz) != ChunkSection.AIR) { sy = y; break; }
                }
                surfaceYGrid[lz * 16 + lx] = sy;
            }
        }

        SurfaceContext ctx = new SurfaceContext();
        ctx.minY          = minY;
        ctx.maxY          = maxY;
        ctx.seaLevel      = seaLevel;
        ctx.noises        = noiseCache;
        ctx.randomFactory = randomFactory;
        ctx.worldgenData  = worldgenData;
        ctx.surfaceYGrid  = surfaceYGrid;

        for (int lz = 0; lz < 16; lz++) {
            for (int lx = 0; lx < 16; lx++) {
                int worldX = chunkX * 16 + lx;
                int worldZ = chunkZ * 16 + lz;

                ctx.biomeName = biomeNames[lz * 16 + lx];
                ctx.surfaceY  = surfaceYGrid[lz * 16 + lx];
                ctx.preliminarySurfaceLevel = nc.getPreliminarySurfaceLevel(worldX >> 2, worldZ >> 2);
                ctx.surfaceDepth = (int)(surfaceNoise.getValue(worldX, 0, worldZ) * 2.75 + 3.0
                    + randomFactory.at(worldX, 0, worldZ).nextDouble() * 0.25);

                // waterY: highest y that is water
                ctx.waterY = -1;
                for (int y = maxY; y >= minY; y--) {
                    if (col.getBlock(lx, y, lz) == waterId) { ctx.waterY = y; break; }
                }

                ctx.blockX = worldX;
                ctx.blockZ = worldZ;
                ctx.stoneDepthAbove = 0;

                for (int y = maxY; y >= minY; y--) {
                    ctx.blockY = y;
                    boolean solid = (col.getBlock(lx, y, lz) != ChunkSection.AIR);
                    ctx.stoneDepthAbove = solid ? ctx.stoneDepthAbove + 1 : 0;
                    // stoneDepthBelow: count solid blocks going down from y-1
                    ctx.stoneDepthBelow = 0;
                    if (solid) {
                        for (int by = y - 1; by >= minY; by--) {
                            if (col.getBlock(lx, by, lz) != ChunkSection.AIR) ctx.stoneDepthBelow++;
                            else break;
                        }
                    }

                    Integer result = eval(rule, ctx);
                    if (result != null) col.setBlock(lx, y, lz, result);
                }
            }
        }
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
            case SurfaceRule.IfTrue ifTrue ->
                evalCondition(ifTrue.condition(), ctx) ? eval(ifTrue.then(), ctx) : null;
            case SurfaceRule.Block block -> block.blockStateId();
            case SurfaceRule.Bandlands __ -> null; // terracotta stripes — M4g-c1 범위 외
        };
    }

    private boolean evalCondition(SurfaceRule.SurfaceCondition cond, SurfaceContext ctx) {
        return switch (cond) {
            case SurfaceRule.SurfaceCondition.VerticalGradient vg -> evalVerticalGradient(vg, ctx);
            case SurfaceRule.SurfaceCondition.AbovePreliminarySurface __ ->
                ctx.blockY >= ctx.preliminarySurfaceLevel;
            case SurfaceRule.SurfaceCondition.StoneDepth sd -> evalStoneDepth(sd, ctx);
            case SurfaceRule.SurfaceCondition.BiomeIs bi -> bi.biomes().contains(ctx.biomeName);
            case SurfaceRule.SurfaceCondition.YAbove ya -> evalYAbove(ya, ctx);
            case SurfaceRule.SurfaceCondition.Water w  -> evalWater(w, ctx);
            case SurfaceRule.SurfaceCondition.NoiseThreshold nt -> evalNoiseThreshold(nt, ctx);
            case SurfaceRule.SurfaceCondition.Temperature __ ->
                ctx.worldgenData.biomeTemperature(ctx.biomeName) < 0.15f;
            case SurfaceRule.SurfaceCondition.Steep __ -> evalSteep(ctx);
            case SurfaceRule.SurfaceCondition.Not n -> !evalCondition(n.inner(), ctx);
            case SurfaceRule.SurfaceCondition.Hole __ -> ctx.surfaceY < ctx.seaLevel;
        };
    }

    private boolean evalVerticalGradient(SurfaceRule.SurfaceCondition.VerticalGradient vg,
                                          SurfaceContext ctx) {
        int trueY  = resolveY(vg.trueAtAndBelow(), ctx);
        int falseY = resolveY(vg.falseAtAndAbove(), ctx);
        if (ctx.blockY <= trueY)  return true;
        if (ctx.blockY >= falseY) return false;
        double gradient = (double)(ctx.blockY - trueY) / (double)(falseY - trueY);
        XoroshiroRandomSource rng = randomFactory.fromHashOf(vg.randomName()).forkPositional().at(ctx.blockX, ctx.blockY, ctx.blockZ);
        return rng.nextFloat() < (1.0f - (float)gradient);
    }

    private boolean evalStoneDepth(SurfaceRule.SurfaceCondition.StoneDepth sd, SurfaceContext ctx) {
        int depth = switch (sd.surfaceType()) {
            case FLOOR   -> ctx.stoneDepthAbove;
            case CEILING -> ctx.stoneDepthBelow;
        };
        int maxDepth = sd.offset() + 1;
        if (sd.addSurfaceDepth()) maxDepth += ctx.surfaceDepth;
        return depth <= maxDepth;
    }

    private boolean evalYAbove(SurfaceRule.SurfaceCondition.YAbove ya, SurfaceContext ctx) {
        int y = resolveY(ya.anchor(), ctx);
        if (ya.addStoneDepth()) y += ctx.stoneDepthAbove;
        return ctx.blockY >= y + ya.surfaceDepthMultiplier() * ctx.surfaceDepth;
    }

    private boolean evalWater(SurfaceRule.SurfaceCondition.Water w, SurfaceContext ctx) {
        if (ctx.waterY == -1) return w.offset() < 0;
        return ctx.waterY >= ctx.blockY + w.offset();
    }

    private boolean evalNoiseThreshold(SurfaceRule.SurfaceCondition.NoiseThreshold nt,
                                        SurfaceContext ctx) {
        NormalNoise noise = buildNoise(nt.noiseId());
        double v = noise.getValue(ctx.blockX, 0, ctx.blockZ);
        return v >= nt.minInclusive() && v < nt.maxExclusive();
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
