package org.bukkit.craftbukkit.generator;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;
import org.domi.furnace.api.worldgen.GenerationChunk;
import org.domi.furnace.compat.paper.BukkitChunkGenerators;
import org.domi.furnace.compat.paper.Compat;
import org.domi.furnace.compat.paper.CompatBlockData;

/**
 * Paper OldCraftChunkData({@code Server#createChunkData}) — 옛 generateChunkData 가 돌려주는 섹션 묶음. 손댄 섹션(16칸 높이)만 생기고,
 * 생긴 섹션은 청크의 그 섹션 블록을 통째로 갈아 끼운다(안 놓은 칸은 공기, 바이옴은 그대로). T-264 P2c-3, 옛 BukkitChunkGenerators$OldChunkData.
 * 생성자·{@link #applyTo} 는 Furnace 전용이라 패키지 전용이고 호환 계층은 CraftAccess 로 부른다.
 */
@Deprecated
public final class OldCraftChunkData implements ChunkGenerator.ChunkData {
    private final int minHeight, maxHeight;
    private final org.domi.furnace.api.world.BlockData[][] sections;

    OldCraftChunkData(int minHeight, int maxHeight) {
        this.minHeight = minHeight;
        this.maxHeight = maxHeight;
        this.sections = new org.domi.furnace.api.world.BlockData[(((maxHeight - 1) >> 4) + 1) - (minHeight >> 4)][];
    }

    @Override public int getMinHeight() { return minHeight; }
    @Override public int getMaxHeight() { return maxHeight; }

    @Override
    public Biome getBiome(int x, int y, int z) { throw new UnsupportedOperationException("Unsupported, in older chunk generator api"); }

    @Override public void setBlock(int x, int y, int z, Material material) { setBlock(x, y, z, material.createBlockData()); }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) { set(x, y, z, BukkitChunkGenerators.furnaceData(blockData)); }

    private void set(int x, int y, int z, org.domi.furnace.api.world.BlockData d) {
        if (x != (x & 0xf) || y < minHeight || y >= maxHeight || z != (z & 0xf)) return;
        section(y)[index(x, y, z)] = d;
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, Material material) {
        setRegion(xMin, yMin, zMin, xMax, yMax, zMax, material.createBlockData());
    }

    @Override
    public void setRegion(int xMin, int yMin, int zMin, int xMax, int yMax, int zMax, BlockData blockData) {
        var d = BukkitChunkGenerators.furnaceData(blockData);
        int[] r = BukkitChunkGenerators.clamp(xMin, yMin, zMin, xMax, yMax, zMax, minHeight, maxHeight);
        if (r == null) return;
        for (int y = r[1]; y < r[4]; y++) {
            var s = section(y);
            for (int x = r[0]; x < r[3]; x++) {
                for (int z = r[2]; z < r[5]; z++) s[index(x, y, z)] = d;
            }
        }
    }

    @Override
    public Material getType(int x, int y, int z) { return Compat.material(data(x, y, z).type()); }

    @Override
    public BlockData getBlockData(int x, int y, int z) { return CompatBlockData.of(data(x, y, z)); }

    private org.domi.furnace.api.world.BlockData data(int x, int y, int z) {
        if (x != (x & 0xf) || y < minHeight || y >= maxHeight || z != (z & 0xf)) return org.domi.furnace.api.world.BlockData.AIR;
        var s = sections[(y - minHeight) >> 4];
        if (s == null) return org.domi.furnace.api.world.BlockData.AIR;
        var d = s[index(x, y, z)];
        return d == null ? org.domi.furnace.api.world.BlockData.AIR : d;
    }

    private org.domi.furnace.api.world.BlockData[] section(int y) {
        int i = (y - minHeight) >> 4;
        var s = sections[i];
        if (s == null) sections[i] = s = new org.domi.furnace.api.world.BlockData[4096];
        return s;
    }

    private static int index(int x, int y, int z) { return (y & 0xf) << 8 | z << 4 | x; }

    /** 생긴 섹션을 청크에 갈아 끼운다(CustomChunkGenerator#buildSurface 의 옛 길). */
    void applyTo(GenerationChunk chunk) {
        int base = chunk.minY();
        int count = Math.min(sections.length, (chunk.maxY() + 1 - base + 15) >> 4);
        for (int sec = 0; sec < count; sec++) {
            var s = sections[sec];
            if (s == null) continue;
            int y0 = base + (sec << 4);
            for (int i = 0; i < 4096; i++) {
                var d = s[i];
                chunk.setBlock(i & 15, y0 + (i >> 8), (i >> 4) & 15, d == null ? org.domi.furnace.api.world.BlockData.AIR : d);
            }
        }
    }
}
