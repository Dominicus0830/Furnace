package org.domi.furnace.server.world.gen.pipeline;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkPipelineConcurrencyTest {

    @Test
    void generateFull_isDeterministicAcrossRuns() throws Exception {
        NoiseWorldSource source = source(9999L);
        ChunkPipeline pipeline = new ChunkPipeline(source);

        ChunkColumn first = pipeline.generateFull(2, 3).join();
        ChunkColumn second = pipeline.generateFull(2, 3).join();

        assertChunksEqual(first, second, "determinism seed=9999 cx=2 cz=3");
    }

    @Test
    void generateFull_completesFourChunksConcurrently() throws Exception {
        NoiseWorldSource source = source(9999L);
        ChunkPipeline pipeline = new ChunkPipeline(source);

        List<CompletableFuture<ChunkColumn>> futures = List.of(
            pipeline.generateFull(0, 0),
            pipeline.generateFull(1, 0),
            pipeline.generateFull(0, 1),
            pipeline.generateFull(-1, -1)
        );

        assertDoesNotThrow(() -> CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join());
        for (CompletableFuture<ChunkColumn> future : futures) {
            future.join();
        }
    }

    @Test
    void generateFull_matchesGenerateWithFeatures() throws Exception {
        NoiseWorldSource source = source(1234L);

        ChunkColumn expected = source.generateWithFeatures(0, 0);
        ChunkColumn actual = new ChunkPipeline(source).generateFull(0, 0).join();

        assertChunksEqual(expected, actual, "generateFull vs generateWithFeatures seed=1234 cx=0 cz=0");
    }

    private static NoiseWorldSource source(long seed) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        return NoiseWorldSource.overworld(seed, reg, registries);
    }

    private static void assertChunksEqual(ChunkColumn expected, ChunkColumn actual, String message) {
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

        assertEquals(0, mismatchCount, message + " mismatches=" + mismatchCount
            + (mismatches.isEmpty() ? "" : " first5=" + mismatches));
    }
}
