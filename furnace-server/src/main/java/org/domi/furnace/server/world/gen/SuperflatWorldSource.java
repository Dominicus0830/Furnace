// 클래식 superflat 생성: bedrock/dirt×2/grass 레이어. 좌표 무관 동일 컬럼.
package org.domi.furnace.server.world.gen;

import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

public final class SuperflatWorldSource implements WorldSource {

    private final int bedrock;
    private final int dirt;
    private final int grass;
    private final int biomeId;

    public SuperflatWorldSource(BlockRegistry reg, int biomeId) {
        this.bedrock = reg.defaultStateId("minecraft:bedrock");
        this.dirt = reg.defaultStateId("minecraft:dirt");
        this.grass = reg.defaultStateId("minecraft:grass_block");
        this.biomeId = biomeId;
    }

    @Override
    public ChunkColumn generate(int chunkX, int chunkZ) {
        ChunkColumn c = new ChunkColumn(biomeId);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                c.setBlock(x, -64, z, bedrock);
                c.setBlock(x, -63, z, dirt);
                c.setBlock(x, -62, z, dirt);
                c.setBlock(x, -61, z, grass);
                c.setSurface(x, z, 4); // (−61 − (−64)) + 1
            }
        }
        return c;
    }
}
