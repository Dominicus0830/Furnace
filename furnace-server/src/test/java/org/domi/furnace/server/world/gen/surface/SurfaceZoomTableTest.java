// 표면 규칙 바이옴 조회의 꼭짓점 표(같은 바이옴이면 생략·지터 메모)가 바닐라 getBiome 경로(블록마다 지터 계산)와 블록 단위로 같은지.
package org.domi.furnace.server.world.gen.surface;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class SurfaceZoomTableTest {

    static final BlockRegistry BLOCKS = BlockRegistry.load("blocks/1.21.1.txt");
    static final RegistrySet REGISTRIES = RegistrySet.load("registries/1.21.1.txt");

    /** 원점 근처, 흩어진 곳, 음수, 먼 곳, 월드 경계(청크 ±1874999) 바로 안·바깥. */
    static final int[][] CHUNKS = {
        {0, 0}, {-1, -1}, {3, -7}, {-40, 25}, {117, 88}, {-260, -301}, {1500, -900}, {-12000, 7000},
        {1874999, 1874999}, {-1875000, -1875000}, {1875001, -1874999},
    };

    static List<NoiseWorldSource> sources() {
        return List.of(
            NoiseWorldSource.nether(0L, BLOCKS, REGISTRIES),
            NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES),
            NoiseWorldSource.nether(-7L, BLOCKS, REGISTRIES),
            NoiseWorldSource.overworld(42L, BLOCKS, REGISTRIES),
            NoiseWorldSource.overworld(-1L, BLOCKS, REGISTRIES, "minecraft:large_biomes"),
            NoiseWorldSource.overworld(7L, BLOCKS, REGISTRIES, "minecraft:amplified"));
    }

    @Test
    void everyBlockBiomeMatchesPerBlockJitter() {
        int mixedChunks = 0, checked = 0;
        for (NoiseWorldSource src : sources()) {
            SurfaceSystem ss = src.surfaceSystem();
            for (int[] c : CHUNKS) {
                // 이웃 청크와 바이옴이 섞이는 곳을 더 넣는다(바이옴 경계를 지나는 줄)
                for (int d = 0; d < 3; d++) {
                    int cx = c[0] + d * 5, cz = c[1] - d * 3;
                    int[] biomeIds = src.generateWithBiomes(cx, cz).biomeIds();
                    int[] fast = ss.blockBiomesForTest(biomeIds, cx, cz, true);
                    int[] slow = ss.blockBiomesForTest(biomeIds, cx, cz, false);
                    assertArrayEquals(slow, fast, "청크 " + cx + "," + cz);
                    Set<Integer> distinct = new HashSet<>();
                    for (int b : slow) distinct.add(b);
                    if (distinct.size() > 1) mixedChunks++;
                    checked++;
                }
            }
        }
        assertEquals(sources().size() * CHUNKS.length * 3, checked);
        // 바이옴이 섞인 청크(지터 거리 계산 경로)를 충분히 거쳤는지
        assertTrue(mixedChunks >= 20, "바이옴 경계 청크가 너무 적다: " + mixedChunks);
    }

    @Test
    void generatedChunksMatchWithTableOff() throws Exception {
        List<NoiseWorldSource> fast = new ArrayList<>(), slow = new ArrayList<>();
        fast.add(NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES));
        fast.add(NoiseWorldSource.overworld(42L, BLOCKS, REGISTRIES));
        System.setProperty("furnace.surface.zoomTable", "false");
        try {
            slow.add(NoiseWorldSource.nether(12345L, BLOCKS, REGISTRIES));
            slow.add(NoiseWorldSource.overworld(42L, BLOCKS, REGISTRIES));
        } finally {
            System.clearProperty("furnace.surface.zoomTable");
        }
        // 여러 스레드가 같은 생성기로 동시에 만든 청크도 같아야 한다(표는 호출마다 따로)
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            for (int k = 0; k < fast.size(); k++) {
                NoiseWorldSource f = fast.get(k), s = slow.get(k);
                List<Future<ChunkColumn>> futures = new ArrayList<>();
                List<int[]> where = new ArrayList<>();
                for (int[] c : CHUNKS) {
                    for (int d = 0; d < 2; d++) {
                        int cx = c[0] + d, cz = c[1];
                        where.add(new int[]{cx, cz});
                        futures.add(pool.submit(() -> f.generateWorldChunk(cx, cz).col()));
                    }
                }
                for (int i = 0; i < where.size(); i++) {
                    int cx = where.get(i)[0], cz = where.get(i)[1];
                    ChunkColumn a = futures.get(i).get(), b = s.generateWorldChunk(cx, cz).col();
                    for (int y = a.minY(); y <= a.maxY(); y++)
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                if (a.getBlock(x, y, z) != b.getBlock(x, y, z))
                                    fail("청크 " + cx + "," + cz + " (" + x + "," + y + "," + z + ") 가 다르다");
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
