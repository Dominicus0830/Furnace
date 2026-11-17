// T-203 임시 월드의 원천: 디스크 없이 밑 생성기로 늘 새로 만든다. 디스크 원천(AnvilWorldSource)이 저장된 청크가 없을 때 하는 생성과 같은 길이다
// (노이즈 생성기는 장식까지 한 완성 청크 + 생성 때 동물 표시, 그 밖은 밑 생성기 그대로).
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.ChunkColumn;

public final class ScratchWorldSource implements WorldSource {

    private final WorldSource base;

    public ScratchWorldSource(WorldSource base) {
        if (base instanceof AnvilWorldSource || base instanceof ScratchWorldSource) throw new IllegalArgumentException("wrap the generator, not " + base);
        this.base = base;
    }

    public WorldSource base() { return base; }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        if (base instanceof NoiseWorldSource n) {
            ChunkColumn col = n.generateWithFeatures(chunkX, chunkZ);
            // 노이즈 생성기만 청크 생성 때 동물을 놓는다(NoiseBasedChunkGenerator.spawnOriginalMobs — AnvilWorldSource 와 같다)
            if (!n.isFlat()) col.freshlyGenerated(true);
            return col;
        }
        return base.generate(chunkX, chunkZ);
    }

    @Override
    public WorldSource freshCopy(long seed) { return new ScratchWorldSource(base.freshCopy(seed)); }

    @Override
    public int noiseBiome(int quartX, int quartY, int quartZ) { return base.noiseBiome(quartX, quartY, quartZ); }

    @Override
    public WorldSource freshCopy(long seed, org.domi.furnace.api.worldgen.BiomeProvider biomes) { return new ScratchWorldSource(base.freshCopy(seed, biomes)); }
}
