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
 * Paper CraftChunkData — 새 생성기 API(generateNoise·Surface·Bedrock·Caves)가 받는, 생성 중인 청크 하나를 직접 고치는 ChunkData.
 * 그 단계가 끝나면 {@link #breakLink} 로 끊긴다(T-264 P2c-3, 옛 BukkitChunkGenerators$LiveChunkData). 생성자는 Furnace 손잡이를 받아
 * 패키지 전용이고 호환 계층은 CraftAccess 로 만든다.
 */
public final class CraftChunkData implements ChunkGenerator.ChunkData {
    private final int minHeight, maxHeight;
    private volatile GenerationChunk chunk;

    CraftChunkData(GenerationChunk chunk) {
        this.chunk = chunk;
        this.minHeight = chunk.minY();
        this.maxHeight = chunk.maxY() + 1;
    }

    public void breakLink() { chunk = null; }

    private GenerationChunk handle() {
        GenerationChunk c = chunk;
        if (c == null) throw new IllegalStateException("IChunkAccess no longer present, are you using it in a different tick?");
        return c;
    }

    private boolean outside(int x, int y, int z) { return x != (x & 0xf) || y < minHeight || y >= maxHeight || z != (z & 0xf); }

    @Override public int getMinHeight() { return minHeight; }
    @Override public int getMaxHeight() { return maxHeight; }

    @Override
    public Biome getBiome(int x, int y, int z) {
        // ChunkAccess#getNoiseBiome(x>>2, y>>2, z>>2): x·z 는 4칸 단위로 감기고 y 는 높이 안으로 잘린다
        return BukkitChunkGenerators.biomeOf(handle().biome(x & 15, Math.clamp(y, minHeight, maxHeight - 1), z & 15));
    }

    @Override public void setBlock(int x, int y, int z, Material material) { setBlock(x, y, z, material.createBlockData()); }

    @Override
    public void setBlock(int x, int y, int z, BlockData blockData) {
        var d = BukkitChunkGenerators.furnaceData(blockData);
        if (outside(x, y, z)) return;
        handle().setBlock(x, y, z, d);
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
        handle().fill(r[0], r[1], r[2], r[3] - 1, r[4] - 1, r[5] - 1, d);
    }

    @Override
    public Material getType(int x, int y, int z) {
        if (outside(x, y, z)) return Material.AIR;
        return Compat.material(handle().blockType(x, y, z));
    }

    @Override
    public BlockData getBlockData(int x, int y, int z) {
        if (outside(x, y, z)) return CompatBlockData.of(org.domi.furnace.api.world.BlockData.AIR);
        return CompatBlockData.of(handle().block(x, y, z));
    }
}
