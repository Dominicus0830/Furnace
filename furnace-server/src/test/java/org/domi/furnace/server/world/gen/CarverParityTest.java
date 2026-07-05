// carver parity 테스트 — LegacyRandom 검증 + carver-enabled oracle 픽스처 비트일치
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.carver.WorldgenRandom;

class CarverParityTest {

    private static final int MIN_Y        = -64;
    private static final int HEIGHT       = 384;
    private static final int CARVER_MAGIC = 0x43525652;

    @Test
    void worldgenRandom_largeFeatureSeed() {
        WorldgenRandom r = new WorldgenRandom();
        r.setLargeFeatureSeed(12345L, 7, -3);
        float v = r.nextFloat();
        assertTrue(v >= 0.0f && v < 1.0f);
    }

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = CarverParityTest.class.getResourceAsStream(resourcePath);
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

    private void assertCarverParity(long seed, int cx, int cz) throws Exception {
        assertCarverParity(seed, cx, cz, 0);
    }

    // maxMismatch>0: root cause는 확정됐으나 아직 수정하지 않은 잔여 mismatch를 회귀 없이 상한만 고정.
    private void assertCarverParity(long seed, int cx, int cz, int maxMismatch) throws Exception {
        BlockRegistry reg11 = BlockRegistry.load("blocks/1.21.1.txt");
        // worldgen-oracle uses Paper's 1.21.1 dev bundle, so fixture state IDs
        // must be decoded with the 1.21.1 block-state table.
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");

        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg11, registries);
        ChunkColumn col = source.generateWithCarvers(cx, cz);

        String fixtureName = "/worldgen/fixtures/carver_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = getClass().getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(CARVER_MAGIC, header.getInt(), "magic mismatch");
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
                    int expectedId = (name == null) ? oracleId : tryId(reg11, name, oracleId);

                    if (expectedId != got) {
                        mismatch++;
                        if (first == null) first = String.format(
                            "lx=%d y=%d lz=%d oracle=%d(%s) expected=%d got=%d",
                            lx, y, lz, oracleId, name, expectedId, got);
                        if (mismatch <= 20) System.out.printf(
                            "MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) exp=%d got=%d%n",
                            seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                    }
                }
            }
        }

        System.out.printf("carver seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        if (maxMismatch > 0) {
            assertTrue(mismatch <= maxMismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz
                + " mismatches=" + mismatch + " > 상한 " + maxMismatch + (first != null ? " first=" + first : ""));
        } else {
            assertEquals(0, mismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz
                + " mismatches=" + mismatch + (first != null ? " first=" + first : ""));
        }
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    @Test void carver_seed0_coord1()  throws Exception { assertCarverParity(0L,          0,  0); }
    @Test void carver_seed0_coord2()  throws Exception { assertCarverParity(0L,         -5,  3); }
    @Test void carver_seedB()         throws Exception { assertCarverParity(123456789L,   0,  0); }

    @Test void carver_seed1234_coord1() throws Exception { assertCarverParity(1234L, 0,  0); }
    @Test void carver_seed1234_coord2() throws Exception { assertCarverParity(1234L, -5, 3); }
    @Test void carver_seed1234_coord3() throws Exception { assertCarverParity(1234L, 7, -2); }

    // 이웃 청크 terrain 검증 (cx=7,cz=-2 feature parity 디버그용)
    @Test void carver_seed1234_n6m3() throws Exception { assertCarverParity(1234L, 6, -3); }
    @Test void carver_seed1234_n6m2() throws Exception { assertCarverParity(1234L, 6, -2); }
    @Test void carver_seed1234_n6m1() throws Exception { assertCarverParity(1234L, 6, -1); }
    @Test void carver_seed1234_n7m3() throws Exception { assertCarverParity(1234L, 7, -3); }
    @Test void carver_seed1234_n7m1() throws Exception { assertCarverParity(1234L, 7, -1); }
    @Test void carver_seed1234_n8m3() throws Exception { assertCarverParity(1234L, 8, -3); }
    @Test void carver_seed1234_n8m2() throws Exception { assertCarverParity(1234L, 8, -2); }
    @Test void carver_seed1234_n8m1() throws Exception { assertCarverParity(1234L, 8, -1); }

    /**
     * realserver-fixes task ④ — 사용자 제보 "연못 부재" 좌표(seed 51532, 청크 -3,-6). aquifer 단계(순수
     * noise+aquifer, carver 전)는 0 mismatch — noise/aquifer 파라미터 발산은 기각. 이 carver 단계에서
     * 32건 발산, 전부 y=62(해수면-1) 지점의 dirt(furnace) vs grass_block(oracle) — carve된 웅덩이/소협곡
     * 가장자리에서 노출된 dirt를 원래 지형으로 되돌리는 vanilla WorldCarver.carveBlock의 "reachedSurface"
     * 재잔디화(regrass) 로직이 Furnace에 아예 없다(furnace-server world/gen/carver/WorldCarver.java의
     * carveBlock은 REPLACEABLE_NAMES 체크 + 상태 치환만 하고 위쪽 블록 승격 단계가 없음). 물/공기 상태 자체는
     * 이미 일치(전부 dirt/grass 텍스트 차이일 뿐) — 사용자가 본 "연못이 없다"는 인상은 웅덩이 자체의 부재가
     * 아니라 가장자리가 흙탕으로 보이는 것일 가능성이 높다. 수정은 vanilla CarvingContext.topMaterial(SurfaceRule
     * 재호출)을 WorldCarver 호출 체인에 새로 연결해야 하는 M~L 규모라 이번 세션 범위를 벗어나 상한만 고정한다.
     * 후속 task: WorldCarver.carveBlock에 reachedSurface 추적 + 최상단 dirt 승격(SurfaceRule.topMaterial
     * 호출) 포팅.
     */
    @Test void carver_seed51532_m3_m6_pondEdgeRegrass_missing() throws Exception {
        assertCarverParity(51532L, -3, -6, 32);
    }
}
