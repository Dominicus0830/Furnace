// 바닐라 Structure.GenerationContext — 구조물 하나의 시작점/조각 생성에 필요한 문맥.
package org.domi.furnace.server.world.gen.structure;

import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

public final class GenContext {
    public final StructureEngine engine;
    public final StructureRegistry.Def def;
    public final long seed;
    public final int chunkX, chunkZ;
    public final WorldgenRandom random;
    public final TerrainAccess terrain;

    public GenContext(StructureEngine engine, StructureRegistry.Def def, long seed, int chunkX, int chunkZ, TerrainAccess terrain) {
        this.engine = engine;
        this.def = def;
        this.seed = seed;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.terrain = terrain;
        this.random = new WorldgenRandom();
        this.random.setLargeFeatureSeed(seed, chunkX, chunkZ);
    }

    public int minBlockX() { return chunkX * 16; }

    public int minBlockZ() { return chunkZ * 16; }

    public int middleBlockX() { return chunkX * 16 + 8; }

    public int middleBlockZ() { return chunkZ * 16 + 8; }

    public boolean validBiome(String biome) { return def.biomes().contains(biome); }

    /** Structure.getLowestY(ctx, width, depth) — 청크 최소 코너 기준 4 모서리의 WORLD_SURFACE_WG 최소 높이. */
    public int lowestY(int width, int depth) {
        return lowestY(minBlockX(), minBlockZ(), width, depth);
    }

    public int lowestY(int x, int z, int width, int depth) {
        int hm = TerrainAccess.HM_WORLD_SURFACE_WG;
        return Math.min(Math.min(terrain.firstOccupiedHeight(hm, x, z), terrain.firstOccupiedHeight(hm, x, z + depth)),
            Math.min(terrain.firstOccupiedHeight(hm, x + width, z), terrain.firstOccupiedHeight(hm, x + width, z + depth)));
    }

    /** Structure.getLowestYIn5by5BoxOffset7Blocks — 회전에 따라 부호가 바뀌는 5x5 상자 코너 높이 최소값. */
    public int lowestYIn5by5BoxOffset7Blocks(int rotation) {
        int dx = 5, dz = 5;
        if (rotation == org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_CW90) {
            dx = -5;
        } else if (rotation == org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_CW180) {
            dx = -5;
            dz = -5;
        } else if (rotation == org.domi.furnace.server.world.gen.structure.template.BlockStateTable.ROT_CCW90) {
            dz = -5;
        }
        int x = chunkX * 16 + 7;
        int z = chunkZ * 16 + 7;
        return lowestY(x, z, dx, dz);
    }

    public int lowestYIn5by5BoxOffset7BlocksX() { return chunkX * 16 + 7; }

    public int lowestYIn5by5BoxOffset7BlocksZ() { return chunkZ * 16 + 7; }
}
