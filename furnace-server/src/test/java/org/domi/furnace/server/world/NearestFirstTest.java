// 청크 로드 순서가 중심거리순(nearest-first)인지 검증 — 스폰 청크 먼저 전송
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearestFirstTest {

    @Test
    void orderNearestFirst_centerFirst_thenMonotonic() {
        ChunkPos center = new ChunkPos(5, 5);
        List<ChunkPos> chunks = new ArrayList<>(List.of(
                new ChunkPos(5, 7), new ChunkPos(8, 5), new ChunkPos(5, 5),
                new ChunkPos(6, 5), new ChunkPos(5, 6)));
        Region.orderNearestFirst(center, chunks);

        assertEquals(center, chunks.get(0), "중심 청크가 먼저");
        int prev = -1;
        for (ChunkPos cp : chunks) {
            int dx = cp.x() - 5, dz = cp.z() - 5;
            int d = dx * dx + dz * dz;
            assertTrue(d >= prev, "거리 단조 증가");
            prev = d;
        }
    }
}
