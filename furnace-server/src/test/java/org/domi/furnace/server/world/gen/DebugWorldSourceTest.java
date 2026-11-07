// 월드 프리셋 debug_all_block_states(DebugWorldSource)와 single_biome_surface(고정 바이옴 노이즈 월드).
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.structure.TerrainAccess;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DebugWorldSourceTest {

    static BlockRegistry blocks;
    static RegistrySet registries;
    static int plains;

    @BeforeAll
    static void load() {
        blocks = BlockRegistry.load("blocks/1.21.1.txt");
        registries = RegistrySet.load("registries/1.21.1.txt");
        plains = registries.indexOf("minecraft:worldgen/biome", "minecraft:plains");
    }

    @Test
    void gridMatchesVanillaFormula() {
        DebugWorldSource s = new DebugWorldSource(blocks, plains);
        int n = blocks.totalStateCount();
        assertEquals(26684, n, "1.21.1 블록 상태 수");
        assertEquals(164, s.gridWidth());   // ceil(sqrt(26684)) = 164
        assertEquals(163, s.gridHeight());  // ceil(26684 / 164) = 163
        int air = blocks.defaultStateId("minecraft:air");
        // x, z 가 둘 다 양수 홀수인 칸만 격자 칸
        assertEquals(air, s.stateFor(0, 1));
        assertEquals(air, s.stateFor(2, 1));
        assertEquals(air, s.stateFor(-1, 3));
        assertEquals(air, s.stateFor(1, 2));
        // 상태 번호 |x/2 × 폭 + z/2|
        assertEquals(0, s.stateFor(1, 1));                 // 0: air
        assertEquals(1, s.stateFor(1, 3));                 // 1: stone
        assertEquals(164, s.stateFor(3, 1));
        assertEquals(164 * 5 + 7, s.stateFor(11, 15));
        // 번호가 상태 수를 넘으면 공기, 격자 밖도 공기
        assertEquals(air, s.stateFor(2 * 163 + 1, 2 * 163 + 1));   // 163*164+163 = 26895 > 26684
        assertEquals(air, s.stateFor(2 * 200 + 1, 1));
        assertEquals("minecraft:stone", blocks.nameOf(s.stateFor(1, 3)));
    }

    @Test
    void chunkHasBarrierLayerAndStateLayer() {
        DebugWorldSource s = new DebugWorldSource(blocks, plains);
        ChunkColumn c = s.generate(0, 0);
        int barrier = blocks.defaultStateId("minecraft:barrier");
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(barrier, c.getBlock(x, 60, z), "방벽 " + x + "," + z);
                assertEquals(s.stateFor(x, z), c.getBlock(x, 70, z), "상태 " + x + "," + z);
                assertEquals(0, c.getBlock(x, 61, z));
                assertEquals(0, c.getBlock(x, 69, z));
                assertEquals(0, c.getBlock(x, 71, z));
                assertEquals(0, c.getBlock(x, 59, z));
            }
        }
        assertEquals(1, c.getBlock(1, 70, 3), "stone");
        // 두 번째 청크: 월드 좌표로 식이 이어진다
        ChunkColumn c2 = s.generate(1, 0);
        assertEquals(s.stateFor(17, 1), c2.getBlock(1, 70, 1));
        // 음수 청크는 방벽만
        ChunkColumn neg = s.generate(-1, -1);
        assertEquals(barrier, neg.getBlock(3, 60, 3));
        assertEquals(0, neg.getBlock(3, 70, 3));
    }

    @Test
    void heightmapSeesStateLayerOrBarrier() {
        DebugWorldSource s = new DebugWorldSource(blocks, plains);
        ChunkColumn c = s.generate(0, 0);
        assertEquals(70, c.surfaceBlockY(1, 3), "상태가 있는 칸은 y=70 이 맨 위");
        assertEquals(60, c.surfaceBlockY(0, 0), "공기 칸은 방벽이 맨 위");
    }

    @Test
    void needsHeightCoveringTheLayers() {
        assertThrows(IllegalArgumentException.class, () -> new DebugWorldSource(blocks, plains, 0, 4));   // 0..64 — 70 이 없다
        new DebugWorldSource(blocks, plains, -64, 24);
    }

    @Test
    void singleBiomeSurfaceIsOverworldTerrainWithOneBiome() {
        NoiseWorldSource normal = NoiseWorldSource.overworld(12345L, blocks, registries, "minecraft:overworld");
        NoiseWorldSource single = NoiseWorldSource.overworld(12345L, blocks, registries, "minecraft:overworld", "minecraft:plains");
        int differentBiomeInNormal = 0;
        for (int x = -4096; x <= 4096; x += 512) {
            for (int z = -4096; z <= 4096; z += 512) {
                assertEquals(plains, single.noiseBiomeIdAtQuart(x >> 2, 16, z >> 2), "고정 바이옴 " + x + "," + z);
                if (normal.noiseBiomeIdAtQuart(x >> 2, 16, z >> 2) != plains) differentBiomeInNormal++;
                // 지형 높이는 바이옴 원천과 무관(노이즈 설정이 같다)
                assertEquals(normal.firstOccupiedHeight(TerrainAccess.HM_WORLD_SURFACE_WG, x, z),
                        single.firstOccupiedHeight(TerrainAccess.HM_WORLD_SURFACE_WG, x, z), "높이 " + x + "," + z);
            }
        }
        assertTrue(differentBiomeInNormal > 10, "일반 월드에는 plains 가 아닌 바이옴이 많다: " + differentBiomeInNormal);
        assertEquals(java.util.Set.of(plains), single.possibleBiomeIds());
        assertNotEquals(normal.possibleBiomeIds(), single.possibleBiomeIds());
        assertThrows(IllegalArgumentException.class,
                () -> NoiseWorldSource.overworld(1L, blocks, registries, "minecraft:overworld", "minecraft:nope"));
    }
}
