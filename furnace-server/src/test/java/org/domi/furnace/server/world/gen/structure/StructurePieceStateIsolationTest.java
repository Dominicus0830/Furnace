// 감사 I-1(W-NEW-1) 회귀 — 구조물 조각의 가변 상태(사막 신전 heightPosition·hasPlacedChest·BoundingBox.move)가
// 요청 사이에 새어 나가지 않는지. 같은 생성기에서 이웃 청크를 먼저 독립 경로(generateIsolated)로 만든 뒤 시작 청크를 만들고,
// 새 생성기로 시작 청크만 만든 결과와 블록이 모두 같아야 한다(상자 개수도 같아야 한다).
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class StructurePieceStateIsolationTest {

    // 이웃 선생성 순서 5종(보고서 재현과 같은 구성: 동·서·남·북·대각 먼저)
    private static final int[][][] ORDERS = {
        {{1, 0}}, {{-1, 0}}, {{0, 1}}, {{0, -1}}, {{-1, -1}, {1, 1}, {1, -1}, {-1, 1}},
    };

    @Test
    void desertPyramidUnaffectedByNeighborFirstGeneration() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        java.util.Set<Integer> chest = blocks.stateIdsForBlock("minecraft:chest");
        assertFalse(chest.isEmpty());
        int checked = 0, totalChests = 0;
        for (long seed : new long[] {1234L, 12345L}) {
            NoiseWorldSource probe = NoiseWorldSource.overworld(seed, blocks, registries);
            for (int[] o : new int[][] {{0, 0}}) {
                StructureLocator.Found f = probe.structureLocator()
                    .findNearest(List.of("minecraft:desert_pyramid"), o[0], 64, o[1], 100);
                if (f == null) continue;
                int cx = Math.floorDiv(f.x(), 16), cz = Math.floorDiv(f.z(), 16);
                long ref = hash(NoiseWorldSource.overworld(seed, blocks, registries).generateIsolated(cx, cz), chest);
                int refChests = (int) (ref >>> 56);
                totalChests += refChests;
                for (int[][] order : ORDERS) {
                    NoiseWorldSource s = NoiseWorldSource.overworld(seed, blocks, registries);
                    for (int[] d : order) s.generateIsolated(cx + d[0], cz + d[1]);
                    long got = hash(s.generateIsolated(cx, cz), chest);
                    assertEquals(refChests, (int) (got >>> 56),
                        "seed " + seed + " 청크 " + cx + "," + cz + " 이웃 선생성 후 상자 개수가 다르다");
                    assertEquals(ref, got, "seed " + seed + " 청크 " + cx + "," + cz + " 이웃 선생성 후 블록이 다르다");
                }
                checked++;
            }
        }
        assertTrue(checked >= 2, "사막 신전을 충분히 찾지 못함: " + checked);
        assertTrue(totalChests > 0, "시작 청크에 상자가 하나도 없음 — 시나리오가 상자 경로를 검증하지 못한다");
    }

    /** 상위 8비트 = 상자 개수, 하위 = 블록 FNV 해시. */
    private static long hash(ChunkColumn col, java.util.Set<Integer> chest) {
        long h = 1469598103934665603L;
        int chests = 0;
        for (int y = col.minY(); y <= col.maxY(); y++)
            for (int z = 0; z < 16; z++)
                for (int x = 0; x < 16; x++) {
                    int b = col.getBlock(x, y, z);
                    if (chest.contains(b)) chests++;
                    h = (h ^ b) * 1099511628211L;
                }
        return ((long) chests << 56) | (h & 0x00FFFFFFFFFFFFFFL);
    }
}
