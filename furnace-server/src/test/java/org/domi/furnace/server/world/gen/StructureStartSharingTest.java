// 공유되는 구조물 시작점(빈 결과·jigsaw 전용)이 장식 중에 바뀌지 않는지 — 같은 청크들을 정방향/역방향 순서로
// 각각 새 생성기에서 독립 경로(generateIsolated)로 만들어 블록이 모두 같아야 한다(공유 조각이 변형되면 먼저 만든 청크가
// 나중 청크 결과를 바꾼다). 서버 경로(공유 월드 장식)는 바닐라처럼 순서에 따라 겹치는 장식이 달라질 수 있어 여기서 쓰지 않는다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.structure.JigsawPiece;
import org.domi.furnace.server.world.gen.structure.StructureStart;
import org.junit.jupiter.api.Test;

class StructureStartSharingTest {

    @Test
    void sharedJigsawStartsGiveOrderIndependentChunks() throws Exception {
        BlockRegistry blocks = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource a = NoiseWorldSource.overworld(12345L, blocks, registries);
        NoiseWorldSource b = NoiseWorldSource.overworld(12345L, blocks, registries);

        StructureStart village = null;
        search:
        for (int r = 0; r < 64; r++) {
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++) {
                if (Math.max(Math.abs(x), Math.abs(z)) != r) continue;
                for (StructureStart s : a.applyStructureStarts(x, z)) {
                    if (!s.pieces().isEmpty() && s.pieces().get(0) instanceof JigsawPiece && s.pieces().size() > 3) { village = s; break search; }
                }
            }
        }
        assertNotNull(village, "jigsaw 구조물을 찾지 못함");
        int cx = Math.floorDiv((village.boundingBox().minX() + village.boundingBox().maxX()) / 2, 16);
        int cz = Math.floorDiv((village.boundingBox().minZ() + village.boundingBox().maxZ()) / 2, 16);
        List<int[]> order = new ArrayList<>();
        for (int dz = -2; dz <= 1; dz++) for (int dx = -2; dx <= 1; dx++) order.add(new int[] {cx + dx, cz + dz});

        List<ChunkColumn> fwd = new ArrayList<>();
        for (int[] c : order) fwd.add(a.generateIsolated(c[0], c[1]));
        ChunkColumn[] rev = new ChunkColumn[order.size()];
        for (int i = order.size() - 1; i >= 0; i--) rev[i] = b.generateIsolated(order.get(i)[0], order.get(i)[1]);

        assertTrue(a.cachedStructureStarts(village.chunkX(), village.chunkZ()) != null, "jigsaw 시작점이 공유 캐시에 들어가야 한다");
        for (int i = 0; i < order.size(); i++) {
            ChunkColumn p = fwd.get(i), q = rev[i];
            for (int y = p.minY(); y <= p.maxY(); y++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        if (p.getBlock(x, y, z) != q.getBlock(x, y, z))
                            fail("청크 " + order.get(i)[0] + "," + order.get(i)[1] + " 의 (" + x + "," + y + "," + z + ") 가 생성 순서에 따라 다르다");
        }
    }
}
