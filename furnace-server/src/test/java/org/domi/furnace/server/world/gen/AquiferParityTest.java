// aquifer 비트일치 parity 테스트 (DisabledAquifer 단위 + aquifer-enabled oracle 픽스처 vs NoiseBasedAquifer)
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
import org.domi.furnace.server.world.gen.df.DensityFunction;

class AquiferParityTest {

    private static final int MIN_Y          = -64;
    private static final int HEIGHT         = 384;
    private static final int AQUIFER_MAGIC  = 0x41515546;

    // -----------------------------------------------------------------------
    // DisabledAquifer unit test
    // -----------------------------------------------------------------------

    @Test
    void disabledAquifer_sea_level_fill() {
        DisabledAquifer aq = new DisabledAquifer(63, -54, 80, 176);

        // y=50 < seaLevel=63 → water
        assertEquals(80,  aq.computeSubstance(new DensityFunction.SinglePointContext(0,  50, 0), -1.0));
        // y=70 >= seaLevel=63 → air
        assertEquals(0,   aq.computeSubstance(new DensityFunction.SinglePointContext(0,  70, 0), -1.0));
        // y=-60 < lavaFloorY=-54 → lava
        assertEquals(176, aq.computeSubstance(new DensityFunction.SinglePointContext(0, -60, 0), -1.0));
        // y=-54 (boundary, not < lavaFloorY) → water
        assertEquals(80,  aq.computeSubstance(new DensityFunction.SinglePointContext(0, -54, 0), -1.0));
        // y=63 (boundary, not < seaLevel) → air
        assertEquals(0,   aq.computeSubstance(new DensityFunction.SinglePointContext(0,  63, 0), -1.0));
    }

    // -----------------------------------------------------------------------
    // Aquifer parity tests (aquifer-enabled oracle fixtures)
    // -----------------------------------------------------------------------

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = AquiferParityTest.class.getResourceAsStream(resourcePath);
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

    private void assertAquiferParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg11 = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");

        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg11, registries);
        ChunkColumn col = source.generate(cx, cz);

        String fixtureName = "/worldgen/fixtures/aquifer_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(AQUIFER_MAGIC, header.getInt(),  "magic mismatch");
        assertEquals(seed,          header.getLong(), "seed mismatch");
        assertEquals(cx,            header.getInt(),  "cx mismatch");
        assertEquals(cz,            header.getInt(),  "cz mismatch");

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
                        expectedId = oracleId;
                    } else {
                        try { expectedId = reg11.defaultStateId(name); }
                        catch (IllegalArgumentException e) { expectedId = oracleId; }
                    }

                    if (expectedId != got) {
                        mismatch++;
                        System.out.printf("MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) expected=%d got=%d%n",
                            seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                        if (first == null) {
                            first = String.format("lx=%d y=%d lz=%d oracle=%d(%s) expected=%d got=%d",
                                lx, y, lz, oracleId, name, expectedId, got);
                        }
                    }
                }
            }
        }

        System.out.printf("aquifer seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        assertEquals(0, mismatch,
            "seed=" + seed + " cx=" + cx + " cz=" + cz + " mismatches=" + mismatch
            + (first != null ? " first=" + first : ""));
    }

    @Test void aquifer_seed0_coord1() throws Exception { assertAquiferParity(0L,          -20, 10);  }
    @Test void aquifer_seed0_coord2() throws Exception { assertAquiferParity(0L,           15, -30); }
    @Test void aquifer_seed0_coord3() throws Exception { assertAquiferParity(0L,            0, -40); }
    @Test void aquifer_seedB()        throws Exception { assertAquiferParity(123456789L,    0,   0); }

    // ore vein parity — vein 함유 청크(seed0): copper(y0~50). raw_copper는 vein 전용 → veins byte-exact 검증.
    @Test void oreVein_seed0_0_5() throws Exception { assertAquiferParity(0L, 0, 5); } // 113 copper + 4 raw_copper
    @Test void oreVein_seed0_0_4() throws Exception { assertAquiferParity(0L, 0, 4); }
    @Test void oreVein_seed0_1_5() throws Exception { assertAquiferParity(0L, 1, 5); }
    @Test void oreVein_seed0_1_4() throws Exception { assertAquiferParity(0L, 1, 4); }

    // realserver-fixes task ④ — 사용자 제보 연못 부재 좌표(seed 51532, 청크 -3,-6)의 noise+aquifer(carver 전)
    // 단계. 0 mismatch — noise/aquifer 파라미터 발산 가설 기각, 실제 발산은 carver 단계(CarverParityTest 참조).
    @Test void aquifer_seed51532_m3_m6_pondCoord() throws Exception { assertAquiferParity(51532L, -3, -6); }
}
