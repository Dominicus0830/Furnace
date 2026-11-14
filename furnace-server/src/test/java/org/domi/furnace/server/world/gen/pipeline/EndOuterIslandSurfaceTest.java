package org.domi.furnace.server.world.gen.pipeline;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkSection;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

/**
 * 장식 단계까지 간 End 외곽 섬 청크의 최종 WORLD_SURFACE 가 블록에서 센 최상단 비공기와 같다(PR #354 검토 R2).
 * 외곽 섬은 공허(WORLD_SURFACE 0)에서 시작해 장식이 비공기를 놓는다 — 빈 열 갱신 가드가 있으면 그 열이 0 으로 남는다.
 */
class EndOuterIslandSurfaceTest {

    @Test
    void worldSurfaceMatchesBlocksOnOuterIslandChunks() {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.create(NoiseWorldSource.Dimension.END, 1234L, reg, registries);
        ChunkPipeline pipe = new ChunkPipeline(source);
        int[][] chunks = {{64, -3}, {69, 0}};
        int nonEmpty = 0;
        for (int[] pos : chunks) {
            ChunkColumn col = pipe.generateFull(pos[0], pos[1]).join();
            int minY = col.minY(), maxY = minY + col.sectionCount() * 16 - 1;
            for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int truth = minY - 1;
                for (int y = maxY; y >= minY; y--) {
                    if (!ChunkSection.isAir(col.getBlock(x, y, z))) { truth = y; break; }
                }
                if (truth >= minY) nonEmpty++;
                assertEquals(truth, col.surfaceBlockY(x, z),
                    "WORLD_SURFACE 가 블록과 다름 chunk=(" + pos[0] + "," + pos[1] + ") x=" + x + " z=" + z);
            }
        }
        assertEquals(true, nonEmpty > 0, "시험 입력이 비어 있음 — 외곽 섬 청크에 블록이 있어야 한다");
    }
}
