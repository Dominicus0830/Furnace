// 공유 월드 장식(서버 경로): 각 origin 은 한 번만 장식되고, 같은 순서로 요청하면 결과가 같다.
package org.domi.furnace.server.world.gen.pipeline;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class ChunkPipelineSharedTest {

    private static NoiseWorldSource source(long seed) throws Exception {
        return NoiseWorldSource.overworld(seed, BlockRegistry.load("blocks/1.21.1.txt"), RegistrySet.load("registries/1.21.1.txt"));
    }

    @Test
    void eachOriginIsDecoratedOnce() throws Exception {
        NoiseWorldSource s = source(1234L);
        List<CompletableFuture<ChunkColumn>> fs = new ArrayList<>();
        for (int z = -1; z <= 1; z++) for (int x = -1; x <= 1; x++) fs.add(s.pipeline().generateShared(x, z));   // 동시에 요청
        for (CompletableFuture<ChunkColumn> f : fs) assertNotNull(f.join());
        // 3×3 청크를 완성하는 데 필요한 origin 은 5×5 = 25 개 — 독립 경로라면 9×9 = 81 번 장식했다.
        assertEquals(25, s.pipeline().sharedDecorationCount());
    }

    @Test
    void sameRequestOrderGivesSameChunks() throws Exception {
        NoiseWorldSource a = source(777L), b = source(777L);
        for (int z = 3; z <= 5; z++) {
            for (int x = -2; x <= 0; x++) {
                ChunkColumn p = a.generateWithFeatures(x, z), q = b.generateWithFeatures(x, z);
                for (int y = p.minY(); y <= p.maxY(); y++)
                    for (int lx = 0; lx < 16; lx++)
                        for (int lz = 0; lz < 16; lz++)
                            if (p.getBlock(lx, y, lz) != q.getBlock(lx, y, lz)) fail("청크 " + x + "," + z + " 가 다르다");
            }
        }
    }
}
