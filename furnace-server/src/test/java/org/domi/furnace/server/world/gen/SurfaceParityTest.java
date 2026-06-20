// 바닐라 surface 블록 state ID와 Furnace 생성 결과를 비교하는 비트일치 parity 테스트
// worldgen-oracle은 1.21.1 paperDevBundle로 fixture를 생성한다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

class SurfaceParityTest {

    private static final int MIN_Y   = -64;
    private static final int HEIGHT  = 384;
    private static final int SURFACE_MAGIC = 0x53524643;

    /** oracle block state ID → block name (default-state only). */
    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = SurfaceParityTest.class.getResourceAsStream(resourcePath);
             BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] parts = line.split(" ");
                if (parts.length == 2) map.put(Integer.parseInt(parts[1]), parts[0]);
            }
        }
        return map;
    }

    private void assertSurfaceParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg11 = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");

        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg11, registries);
        ChunkColumn col = source.generate(cx, cz);

        String fixtureName = "/worldgen/fixtures/surface_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        // Verify header
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(SURFACE_MAGIC, header.getInt(), "magic mismatch");
        assertEquals(seed, header.getLong(), "seed mismatch");
        assertEquals(cx, header.getInt(), "cx mismatch");
        assertEquals(cz, header.getInt(), "cz mismatch");

        int mismatch = 0;
        String first = null;
        int pos = 20;

        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int oracleId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    int got = col.getBlock(lx, y, lz);

                    String name = oracleIdToName.get(oracleId);
                    int expectedId;
                    if (name == null) {
                        expectedId = oracleId; // unknown state — compare raw (should not happen for overworld)
                    } else {
                        try { expectedId = reg11.defaultStateId(name); }
                        catch (IllegalArgumentException e) { expectedId = oracleId; }
                    }

                    if (expectedId != got) {
                        mismatch++;
                        if (first == null) {
                            first = String.format("lx=%d y=%d lz=%d oracle=%d(%s) expected=%d got=%d",
                                lx, y, lz, oracleId, name, expectedId, got);
                        }
                    }
                }
            }
        }

        System.out.printf("seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        assertEquals(0, mismatch,
            "seed=" + seed + " cx=" + cx + " cz=" + cz + " mismatches=" + mismatch
            + (first != null ? " first=" + first : ""));
    }

    @Test void seed0_spawn() throws Exception { assertSurfaceParity(0L, 0, 0); }
    @Test void seed0_far()   throws Exception { assertSurfaceParity(0L, 5, 5); }
    @Test void seedB()       throws Exception { assertSurfaceParity(123456789L, 0, 0); }

    @Test void seed0_aquifer_surface_coord1() throws Exception { assertSurfaceParity(0L, -20, 10); }
    @Test void seed0_aquifer_surface_coord2() throws Exception { assertSurfaceParity(0L, 15, -30); }
    @Test void seed0_aquifer_surface_coord3() throws Exception { assertSurfaceParity(0L, 0, -40); }
}
