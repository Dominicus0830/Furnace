package org.bukkit.craftbukkit.generator;

import java.util.List;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftRegionAccessor;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.LimitedRegion;
import org.domi.furnace.api.worldgen.DecorationRegion;
import org.domi.furnace.api.worldgen.GenerationHeightmap;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatBlockData;
import org.domi.furnace.compat.paper.UnsupportedFeature;

/**
 * Paper CraftLimitedRegion — 중심 청크 둘레 16칸(3×3 청크), y 는 월드 높이 안. populate 가 끝나면 끊긴다(T-264 P2c: 옛 BukkitChunkGenerators.Region).
 * 상위는 Paper 처럼 {@link CraftRegionAccessor}. Furnace 생성자(월드·생성 영역 손잡이)는 패키지 전용이라 호환 모듈은 CraftAccess 로 만든다.
 */
public class CraftLimitedRegion extends CraftRegionAccessor implements LimitedRegion {
    private static final int BUFFER = 16;
    private final World world;
    private volatile DecorationRegion handle;
    private final int centerX, centerZ;
    private final int minX, minY, minZ, maxX, maxY, maxZ;   // 끝은 제외(BoundingBox#contains)

    CraftLimitedRegion(World world, DecorationRegion handle) {
        this.world = world;
        this.handle = handle;
        this.centerX = handle.originChunkX();
        this.centerZ = handle.originChunkZ();
        this.minX = (centerX << 4) - BUFFER;
        this.minZ = (centerZ << 4) - BUFFER;
        this.maxX = (centerX << 4) + BUFFER + 16;
        this.maxZ = (centerZ << 4) + BUFFER + 16;
        this.minY = handle.minY();
        this.maxY = handle.maxY() + 1;
    }

    /** Paper: 생성 단계가 끝나면 끊는다 — 그 뒤 읽기·쓰기는 IllegalStateException. */
    public void breakLink() { handle = null; }

    private DecorationRegion h() {
        DecorationRegion d = handle;
        if (d == null) throw new IllegalStateException("GeneratorAccessSeed no longer present, are you using it in a different tick?");
        return d;
    }

    private boolean contains(double x, double y, double z) { return x >= minX && x < maxX && y >= minY && y < maxY && z >= minZ && z < maxZ; }

    private void check(int x, int y, int z) {
        if (!isInRegion(x, y, z)) throw new IllegalArgumentException("Coordinates " + x + ", " + y + ", " + z + " are not in the region");
    }

    private void checkColumn(int x, int z) {
        // CraftLimitedRegion: 상자 가운데 y 로 본다
        if (!isInRegion(x, (int) Math.floor((minY + maxY) / 2.0), z)) throw new IllegalArgumentException("Coordinates " + x + ", " + z + " are not in the region");
    }

    @Override public int getBuffer() { return BUFFER; }
    @Override public boolean isInRegion(Location location) { return contains(location.getX(), location.getY(), location.getZ()); }
    @Override public boolean isInRegion(int x, int y, int z) { return contains(x, y, z); }
    @Override public int getCenterChunkX() { return centerX; }
    @Override public int getCenterChunkZ() { return centerZ; }

    /** CraftLimitedRegion: 연결(생성 중 영역)을 거쳐 그 월드 — 끊긴 뒤에는 IllegalStateException. */
    @Override
    public World getWorld() {
        h();
        return world;
    }

    /** CraftRegionAccessor: 그 월드(차원) 키 — 끊긴 뒤에는 IllegalStateException. */
    @Override
    public org.bukkit.NamespacedKey getKey() {
        h();
        return world.getKey();
    }

    @Override
    public List<BlockState> getTileEntities() {
        throw new UnsupportedFeature("LimitedRegion#getTileEntities", "block entities of a region being generated are not available yet (T-218)");
    }

    @Override
    public Biome getBiome(int x, int y, int z) {
        check(x, y, z);
        return org.domi.furnace.compat.paper.BukkitChunkGenerators.biomeOf(h().biome(x, y, z));
    }

    @Override
    public void setBiome(int x, int y, int z, Biome biome) {
        if (biome == Biome.CUSTOM) throw new IllegalArgumentException("Cannot set the biome to " + biome);
        check(x, y, z);
        throw new UnsupportedFeature("LimitedRegion#setBiome", "changing biomes while decorating is not available yet (T-218)");
    }

    @Override
    public BlockData getBlockData(int x, int y, int z) {
        check(x, y, z);
        return CompatBlockData.of(h().block(x, y, z));
    }

    @Override
    public Material getType(int x, int y, int z) {
        check(x, y, z);
        return Compat.material(h().blockType(x, y, z));
    }

    @Override
    public void setBlockData(int x, int y, int z, BlockData blockData) {
        check(x, y, z);
        h().setBlock(x, y, z, org.domi.furnace.compat.paper.BukkitChunkGenerators.furnaceData(blockData));
    }

    @Override
    public int getHighestBlockYAt(int x, int z) {
        checkColumn(x, z);
        return h().height(GenerationHeightmap.MOTION_BLOCKING, x, z);
    }

    @Override
    public int getHighestBlockYAt(Location location) {
        if (!isInRegion(location)) {
            throw new IllegalArgumentException("Coordinates " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + " are not in the region");
        }
        return h().height(GenerationHeightmap.MOTION_BLOCKING, location.getBlockX(), location.getBlockZ());
    }

    @Override
    public int getHighestBlockYAt(int x, int z, HeightMap heightMap) {
        checkColumn(x, z);
        return h().height(GenerationHeightmap.valueOf(heightMap.name()), x, z);
    }

    @Override
    public int getHighestBlockYAt(Location location, HeightMap heightMap) {
        if (!isInRegion(location)) {
            throw new IllegalArgumentException("Coordinates " + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + " are not in the region");
        }
        return h().height(GenerationHeightmap.valueOf(heightMap.name()), location.getBlockX(), location.getBlockZ());
    }
}
