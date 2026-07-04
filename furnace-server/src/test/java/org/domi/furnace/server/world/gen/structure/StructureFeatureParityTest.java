// M4g-e S2c — 사막 피라미드 구조물+feature byte-exact parity: Furnace generateWithFeatures vs oracle featurestruct 픽스처
package org.domi.furnace.server.world.gen.structure;

import static org.junit.jupiter.api.Assertions.*;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.Test;

class StructureFeatureParityTest {

    private static final int MIN_Y               = -64;
    private static final int HEIGHT               = 384;
    private static final int FEATURESTRUCT_MAGIC  = 0x53464C42; // "SFLB", oracle OracleMain.dumpFeatureStructChunk

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = StructureFeatureParityTest.class.getResourceAsStream(resourcePath);
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

    private void assertStructureFeatureParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg11 = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");

        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg11, registries);
        ChunkColumn col = source.generateWithFeatures(cx, cz);

        String fixtureName = "/worldgen/fixtures/featurestruct_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx = loadFixture(fixtureName, seed, cx, cz);

        int mismatch = countMismatches(col, fx, oracleIdToName, reg11, seed, cx, cz);
        System.out.printf("featurestruct seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        assertEquals(0, mismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz
            + " mismatches=" + mismatch);
    }

    private static byte[] loadFixture(String fixtureName, long seed, int cx, int cz) throws Exception {
        byte[] fx;
        try (InputStream in = StructureFeatureParityTest.class.getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }

        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FEATURESTRUCT_MAGIC, header.getInt(), "magic mismatch");
        assertEquals(seed, header.getLong(), "seed mismatch");
        assertEquals(cx, header.getInt(), "cx mismatch");
        assertEquals(cz, header.getInt(), "cz mismatch");
        return fx;
    }

    private static int countMismatches(
        ChunkColumn col,
        byte[] fx,
        Map<Integer, String> oracleIdToName,
        BlockRegistry reg,
        long seed,
        int cx,
        int cz
    ) {
        int mismatch = 0;
        int pos = 20;
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int oracleId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    int got = col.getBlock(lx, y, lz);

                    String name = oracleIdToName.get(oracleId);
                    int expectedId = (name == null) ? oracleId : tryId(reg, name, oracleId);
                    if (expectedId != got) {
                        mismatch++;
                        if (mismatch <= 20) {
                            System.out.printf(
                                "MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) exp=%d got=%d%n",
                                seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                        }
                    }
                }
            }
        }
        return mismatch;
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    /**
     * (a) 피라미드 start 청크 — structplacement_1234_-800_-320_-760_-280.txt에서 desert_pyramids
     * hit으로 직접 확인된 두 start(-781,-299 / -763,-280) 중 -763,-280 채택.
     * 기존 (9,14)는 oracle chest NPE로 유효 start가 생성된 적이 없었고(S3-debug 규명),
     * Furnace·oracle 양쪽 다 구조물 없이 우연히 0 mismatch로 통과했던 것으로 판명 — 폐기.
     */
    @Test void desertPyramidStartChunk() throws Exception { assertStructureFeatureParity(1234L, -763, -280); }

    /**
     * (b) 경계 걸침 이웃 청크 — start는 (-781,-299), bbox x[-12496..-12476] z[-4784..-4764]가
     * 이 청크(-780,-298: x[-12480..-12465] z[-4768..-4753])에 x[-12480..-12476]로 걸친다.
     * references 경로로만 조각을 받는다(자기 start 아님).
     */
    @Test void desertPyramidNeighborChunk() throws Exception { assertStructureFeatureParity(1234L, -780, -298); }

    /** (c) 구조물 없는 청크 — feature 무회귀 확인용(구조물 코드가 feature byte를 안 깨는지). */
    @Test void noStructureChunkRegression() throws Exception { assertStructureFeatureParity(1234L, 0, 0); }
}
