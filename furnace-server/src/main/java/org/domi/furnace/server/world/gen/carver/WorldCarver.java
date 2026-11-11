// 바닐라 WorldCarver 추상 포팅 — carveEllipsoid + carveBlock 공통 로직
package org.domi.furnace.server.world.gen.carver;

import java.util.Set;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.Aquifer;
import org.domi.furnace.server.world.gen.NoiseChunk;
import org.domi.furnace.server.world.gen.NoiseSettings;
import org.domi.furnace.server.world.gen.df.DensityFunction;
import org.domi.furnace.server.world.gen.surface.SurfaceSystem;

public abstract class WorldCarver {

    private static final Set<String> REPLACEABLE_NAMES = Set.of(
        "minecraft:stone","minecraft:granite","minecraft:diorite","minecraft:andesite",
        "minecraft:tuff","minecraft:deepslate",
        "minecraft:dirt","minecraft:grass_block","minecraft:podzol","minecraft:coarse_dirt",
        "minecraft:mycelium","minecraft:rooted_dirt","minecraft:moss_block",
        "minecraft:pale_moss_block","minecraft:mud","minecraft:muddy_mangrove_roots",
        "minecraft:sand","minecraft:red_sand","minecraft:suspicious_sand",
        "minecraft:gravel","minecraft:suspicious_gravel",
        "minecraft:sandstone","minecraft:cut_sandstone","minecraft:chiseled_sandstone","minecraft:smooth_sandstone",
        "minecraft:red_sandstone","minecraft:cut_red_sandstone","minecraft:chiseled_red_sandstone","minecraft:smooth_red_sandstone",
        "minecraft:calcite","minecraft:packed_ice","minecraft:raw_iron_block","minecraft:raw_copper_block",
        "minecraft:water",
        "minecraft:terracotta","minecraft:white_terracotta","minecraft:orange_terracotta","minecraft:magenta_terracotta",
        "minecraft:light_blue_terracotta","minecraft:yellow_terracotta","minecraft:lime_terracotta","minecraft:pink_terracotta",
        "minecraft:gray_terracotta","minecraft:light_gray_terracotta","minecraft:cyan_terracotta","minecraft:purple_terracotta",
        "minecraft:blue_terracotta","minecraft:brown_terracotta","minecraft:green_terracotta","minecraft:red_terracotta",
        "minecraft:black_terracotta",
        "minecraft:iron_ore","minecraft:deepslate_iron_ore","minecraft:copper_ore","minecraft:deepslate_copper_ore",
        "minecraft:snow","minecraft:snow_block","minecraft:powder_snow"
    );

    public abstract boolean isStartChunk(CarverConfig config, LegacyRandom random);

    public abstract boolean carve(
        ChunkColumn col, CarverConfig config, int chunkX, int chunkZ,
        NoiseSettings settings, Aquifer aquifer,
        BlockRegistry reg,
        CarvingMask mask, LegacyRandom random, ChunkPos originChunk,
        SurfaceSystem surfaceSystem, NoiseChunk nc, int[] surfaceYGrid);

    protected static boolean canReach(int targetChunkX, int targetChunkZ,
                                       double x, double z, int step, int maxStep, float thickness) {
        double midX = targetChunkX * 16 + 8;
        double midZ = targetChunkZ * 16 + 8;
        double dx = x - midX;
        double dz = z - midZ;
        double remaining = maxStep - step;
        double radius = thickness + 18.0f; // match vanilla: float add before double widen
        return dx*dx + dz*dz - remaining*remaining <= radius*radius;
    }

    protected int getCarveState(int x, int y, int z, int lavaLevelY, Aquifer aquifer, int lavaId) {
        if (y <= lavaLevelY) return lavaId;
        return aquifer.computeSubstance(new DensityFunction.SinglePointContext(x, y, z), 0.0);
    }

    protected boolean carveBlock(ChunkColumn col, int lx, int y, int lz,
                                  int worldX, int worldZ,
                                  int lavaLevelY, Aquifer aquifer,
                                  CarvingMask mask,
                                  BlockRegistry reg,
                                  int lavaId,
                                  boolean[] reachedSurface,
                                  SurfaceSystem surfaceSystem, NoiseChunk nc, int[] surfaceYGrid,
                                  int chunkX, int chunkZ, int minY) {
        int current = col.getBlock(lx, y, lz);
        String name = reg.nameOf(current);
        if (name.equals("minecraft:grass_block") || name.equals("minecraft:mycelium")) {
            reachedSurface[0] = true;
        }
        if (!REPLACEABLE_NAMES.contains(name)) return false;
        int carved = getCarveState(worldX, y, worldZ, lavaLevelY, aquifer, lavaId);
        if (carved == Aquifer.NO_CARVE) return false;
        col.setBlock(lx, y, lz, carved);
        // 바닐라 WorldCarver.carveBlock: aquifer.shouldScheduleFluidUpdate() 이고 놓은 블록에 유체가 있으면 후처리 표시(A06-03).
        // 용암 높이 아래 칸은 computeSubstance 를 부르지 않으므로 직전 값을 그대로 읽는다(바닐라와 같다).
        if (aquifer.shouldScheduleFluidUpdate()
            && org.domi.furnace.server.world.gen.feature.FluidKinds.shared().of(carved) != org.domi.furnace.server.world.gen.feature.FluidKind.EMPTY) {
            col.markPosForPostprocessing(lx, y, lz);
        }

        // vanilla WorldCarver.carveBlock L151-161 — carve가 표면을 관통하면 노출된 dirt를 topMaterial로 승격.
        if (reachedSurface[0] && y - 1 >= minY) {
            int below = col.getBlock(lx, y - 1, lz);
            if (reg.nameOf(below).equals("minecraft:dirt")) {
                String carvedName = reg.nameOf(carved);
                boolean hasFluid = carvedName.equals("minecraft:water") || carvedName.equals("minecraft:lava");
                Integer top = surfaceSystem.topMaterial(worldX, y - 1, worldZ, hasFluid, nc, chunkX, chunkZ, surfaceYGrid);
                if (top != null) col.setBlock(lx, y - 1, lz, top);
            }
        }
        return true;
    }

    protected boolean carveEllipsoid(
            ChunkColumn col, int chunkX, int chunkZ, CarverConfig config,
            NoiseSettings settings, Aquifer aquifer,
            BlockRegistry reg,
            double x, double y, double z, double hRadius, double vRadius,
            CarvingMask mask, SkipChecker skipChecker, int lavaId,
            SurfaceSystem surfaceSystem, NoiseChunk nc, int[] surfaceYGrid) {

        int chunkMinX = chunkX * 16;
        int chunkMinZ = chunkZ * 16;
        int chunkMidX = chunkMinX + 8;
        int chunkMidZ = chunkMinZ + 8;

        double distCheck = 16.0 + hRadius * 2.0;
        if (Math.abs(x - chunkMidX) > distCheck || Math.abs(z - chunkMidZ) > distCheck) {
            return false;
        }

        int minY  = settings.minY();
        int lxMin = Math.max(0, (int)Math.floor(x - hRadius) - chunkMinX - 1);
        int lxMax = Math.min(15, (int)Math.floor(x + hRadius) - chunkMinX);
        int lzMin = Math.max(0, (int)Math.floor(z - hRadius) - chunkMinZ - 1);
        int lzMax = Math.min(15, (int)Math.floor(z + hRadius) - chunkMinZ);

        int yMinRaw = (int)Math.floor(y - vRadius) - 1;
        int yMaxRaw = (int)Math.floor(y + vRadius) + 1;
        int yMin = Math.max(minY + 1, yMinRaw);
        int yMax = Math.min(minY + settings.height() - 1 - 7, yMaxRaw);

        int lavaLevelY = minY + config.lavaLevelAboveBottom();
        boolean carved = false;

        for (int lx = lxMin; lx <= lxMax; lx++) {
            int worldX = chunkMinX + lx;
            double relX = (worldX + 0.5 - x) / hRadius;

            for (int lz = lzMin; lz <= lzMax; lz++) {
                int worldZ = chunkMinZ + lz;
                double relZ = (worldZ + 0.5 - z) / hRadius;

                if (relX * relX + relZ * relZ >= 1.0) continue;

                // vanilla: MutableBoolean reachedSurface — 컬럼(lx,lz)당 하나, y-loop 전체에서 공유·리셋.
                boolean[] reachedSurface = new boolean[1];
                for (int by = yMax; by > yMin; by--) {
                    double relY = (by - 0.5 - y) / vRadius;
                    if (skipChecker.shouldSkip(relX, relY, relZ, by)) continue;
                    if (mask.get(lx, by, lz)) continue;
                    mask.set(lx, by, lz);
                    carved |= carveBlock(col, lx, by, lz, worldX, worldZ,
                        lavaLevelY, aquifer, mask, reg, lavaId,
                        reachedSurface, surfaceSystem, nc, surfaceYGrid, chunkX, chunkZ, minY);
                }
            }
        }
        return carved;
    }

    @FunctionalInterface
    protected interface SkipChecker {
        boolean shouldSkip(double relX, double relY, double relZ, int y);
    }

    public record ChunkPos(int x, int z) {
        public int getBlockX(int localX) { return x * 16 + localX; }
        public int getBlockZ(int localZ) { return z * 16 + localZ; }
    }
}
