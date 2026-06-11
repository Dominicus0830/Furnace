// 바닐라 surface 블록 state ID와 Furnace 생성 결과를 비교하는 비트일치 parity 테스트
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;

class SurfaceParityTest {

    private static final int MIN_Y   = -64;
    private static final int HEIGHT  = 384;
    private static final int SURFACE_MAGIC = 0x53524643;

    private void assertSurfaceParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);
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

        // Compare block state IDs
        int mismatch = 0;
        String first = null;
        int pos = 20;

        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int expected = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    int got = col.getBlock(lx, y, lz);
                    if (expected != got) {
                        mismatch++;
                        if (first == null) {
                            first = String.format("lx=%d y=%d lz=%d expected=%d got=%d", lx, y, lz, expected, got);
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
}
