// oracle structplacement 덤프(vanilla RandomSpreadStructurePlacement) vs Furnace StructurePlacementCatalog 판정 parity 검증.
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.world.gen.WorldgenData;
import org.junit.jupiter.api.Test;

class StructurePlacementParityTest {

    private static final long SEED = 1234L;

    @Test
    void placementCatalog_matchesVanillaOracleDump() throws Exception {
        List<ChunkFixture> fixture = loadFixture();

        WorldgenData data = WorldgenData.load("worldgen/1.21.1");
        StructurePlacementCatalog catalog = StructurePlacementCatalog.load(data);

        int mismatches = 0;
        boolean sawStructureChunk = false;
        boolean sawEmptyChunk = false;
        for (ChunkFixture cf : fixture) {
            List<String> expected = cf.setIds;
            List<String> actual = new ArrayList<>();
            for (StructureSetPlacement set : catalog.placementsForChunk(SEED, cf.cx, cf.cz)) {
                actual.add(set.setId());
            }
            if (!expected.isEmpty()) sawStructureChunk = true;
            if (expected.isEmpty()) sawEmptyChunk = true;
            if (!expected.equals(actual)) {
                mismatches++;
                if (mismatches <= 5) {
                    System.err.println("mismatch at (" + cf.cx + "," + cf.cz + ") expected=" + expected + " actual=" + actual);
                }
            }
        }
        assertTrue(sawStructureChunk, "fixture에 구조물이 있는 청크가 하나도 없음 — 검증 무의미");
        assertTrue(sawEmptyChunk, "fixture에 구조물 없는 청크가 없음 — 회귀 검증 무의미");
        assertEquals(0, mismatches, mismatches + "개 청크에서 placement 판정 불일치");
    }

    private record ChunkFixture(int cx, int cz, List<String> setIds) {}

    private static List<ChunkFixture> loadFixture() throws Exception {
        try (InputStream in = StructurePlacementParityTest.class.getResourceAsStream(
                "/worldgen/fixtures/structplacement_1234_-64_-64_64_64.txt")) {
            assertNotNull(in, "structplacement fixture 없음 — oracle structplacement:-64:-64:64:64 실행 필요");
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            List<ChunkFixture> result = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] h = line.split(" "); // CHUNK cx cz n
                int cx = Integer.parseInt(h[1]);
                int cz = Integer.parseInt(h[2]);
                int n = Integer.parseInt(h[3]);
                List<String> ids = new ArrayList<>(n);
                for (int i = 0; i < n; i++) ids.add(r.readLine().trim());
                result.add(new ChunkFixture(cx, cz, ids));
            }
            return result;
        }
    }
}
