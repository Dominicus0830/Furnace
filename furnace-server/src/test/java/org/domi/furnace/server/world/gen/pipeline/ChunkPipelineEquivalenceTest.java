package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkPipelineEquivalenceTest {

    @Test
    void generateFull_matchesGenerateWithFeatures_atSpawn() throws Exception {
        assertPipelineParity(1234L, 0, 0);
    }

    @Test
    void generateFull_matchesGenerateWithFeatures_atOffsetChunk() throws Exception {
        assertPipelineParity(1234L, -5, 3);
    }

    private void assertPipelineParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);

        ChunkColumn expected = source.generateWithFeatures(cx, cz);
        ChunkColumn actual = new ChunkPipeline(source).generateFull(cx, cz).join();

        List<String> mismatches = new ArrayList<>(5);
        int mismatchCount = 0;
        for (int y = ChunkColumn.MIN_Y; y < ChunkColumn.MIN_Y + ChunkColumn.SECTION_COUNT * 16; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int want = expected.getBlock(lx, y, lz);
                    int got = actual.getBlock(lx, y, lz);
                    if (want != got) {
                        mismatchCount++;
                        if (mismatches.size() < 5) {
                            mismatches.add("lx=" + lx + " y=" + y + " lz=" + lz + " want=" + want + " got=" + got);
                        }
                    }
                }
            }
        }

        assertEquals(0, mismatchCount,
            "seed=" + seed + " cx=" + cx + " cz=" + cz + " mismatches=" + mismatchCount
                + (mismatches.isEmpty() ? "" : " first5=" + mismatches));
    }
}
