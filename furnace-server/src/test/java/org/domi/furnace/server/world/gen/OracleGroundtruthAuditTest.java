// Phase 0 oracle 감사 — 순정 vanilla 1.21.1 서버가 만든 실제 .mca를 독립 정답지로 삼아
// 기존 oracle featurespill fixture가 옳은지 대조한다. -Dfurnace.oracle.audit.worldDir 미설정 시 skip
// (build.gradle.kts의 test task가 "furnace." 접두 시스템 프로퍼티만 test JVM으로 전달한다).
// 판정용 일회성 도구 — CI 상시 assert 아님(vanilla 실행 없이는 재현 불가하므로 assumeTrue로 skip).
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFile;
import org.junit.jupiter.api.Test;

class OracleGroundtruthAuditTest {

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int FEATURESPILL_MAGIC = 0x46534C42;

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = OracleGroundtruthAuditTest.class.getResourceAsStream(resourcePath);
             java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("#") || line.isBlank()) continue;
                String[] parts = line.split(" ");
                if (parts.length == 2) map.put(Integer.parseInt(parts[1]), parts[0]);
            }
        }
        return map;
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    private ChunkColumn readVanillaChunk(Path worldDir, BlockRegistry reg, int cx, int cz) throws Exception {
        int rx = cx >> 5, rz = cz >> 5;
        Path regionFile = worldDir.resolve("region").resolve("r." + rx + "." + rz + ".mca");
        assertTrue(java.nio.file.Files.exists(regionFile), "region file not found: " + regionFile);
        try (RegionFile rf = new RegionFile(regionFile)) {
            byte[] payload = rf.readChunk(cx, cz);
            assertNotNull(payload, "chunk not generated: " + cx + "," + cz);
            NbtCompound root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(payload)));
            AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
            return codec.fromNbt(root);
        }
    }

    private void auditCoord(long seed, int cx, int cz) throws Exception {
        String worldDirProp = System.getProperty("furnace.oracle.audit.worldDir");
        assumeTrue(worldDirProp != null, "furnace.oracle.audit.worldDir not set - skipping ground-truth audit");
        Path worldDir = Paths.get(worldDirProp);

        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");

        ChunkColumn vanilla = readVanillaChunk(worldDir, reg, cx, cz);

        String fixtureName = "/worldgen/fixtures/featurespill_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = OracleGroundtruthAuditTest.class.getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            fx = in.readAllBytes();
        }
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FEATURESPILL_MAGIC, header.getInt(), "magic mismatch");
        assertEquals(seed, header.getLong(), "seed mismatch");
        assertEquals(cx, header.getInt(), "cx mismatch");
        assertEquals(cz, header.getInt(), "cz mismatch");

        int mismatch = 0;
        int pos = 20;
        Map<String, Integer> pairHist = new TreeMap<>();
        Map<Integer, Integer> yBandHist = new TreeMap<>();
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    int oracleId = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
                    pos += 2;
                    int got = vanilla.getBlock(lx, y, lz);
                    String name = oracleIdToName.get(oracleId);
                    int expectedId = (name == null) ? oracleId : tryId(reg, name, oracleId);
                    if (expectedId != got) {
                        mismatch++;
                        String oracleName = name == null ? ("#" + oracleId) : name;
                        String gotName = reg.canonicalKeyOf(got);
                        if (gotName == null) gotName = "#" + got;
                        String key = oracleName + " -> " + gotName;
                        pairHist.merge(key, 1, Integer::sum);
                        int band = Math.floorDiv(y, 16) * 16;
                        yBandHist.merge(band, 1, Integer::sum);
                        if (mismatch <= 20) {
                            System.out.printf(
                                "AUDIT_MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) vanilla_got=%d(%s)%n",
                                seed, cx, cz, lx, y, lz, oracleId, oracleName, got, gotName);
                        }
                    }
                }
            }
        }

        System.out.printf("ORACLE_AUDIT seed=%d cx=%d cz=%d: %d mismatches / %d blocks%n",
                seed, cx, cz, mismatch, 16 * 16 * HEIGHT);
        if (mismatch > 0) {
            System.out.println("-- mismatch pair histogram (oracle -> vanilla) --");
            pairHist.forEach((k, v) -> System.out.println("  " + k + " : " + v));
            System.out.println("-- mismatch y-band histogram --");
            yBandHist.forEach((k, v) -> System.out.println("  y=[" + k + "," + (k + 16) + ") : " + v));
        }
    }

    @Test void audit_seed1234_0_0() throws Exception { auditCoord(1234L, 0, 0); }
    @Test void audit_seed1234_m5_3() throws Exception { auditCoord(1234L, -5, 3); }
    @Test void audit_seed1234_7_m2() throws Exception { auditCoord(1234L, 7, -2); }
}
