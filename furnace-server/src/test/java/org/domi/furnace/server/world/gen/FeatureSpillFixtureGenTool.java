// mca-groundtruth-migration — 순정 vanilla 실서버 .mca를 featurespill fixture(.bin)로 변환하는 도구.
// oracle 대신 실서버를 정답지로 쓰기 위함. fixture 포맷은 기존과 동일(FSLB 헤더 20바이트 +
// y(-64..319)/lz/lx 순 u16 LE stateId) — OracleGroundtruthAuditTest.readVanillaChunk과 동일 경로로 읽는다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.anvil.AnvilChunkCodec;
import org.domi.furnace.server.world.anvil.RegionFile;
import org.junit.jupiter.api.Test;

class FeatureSpillFixtureGenTool {

    private static final int MIN_Y = -64;
    private static final int HEIGHT = 384;
    private static final int FEATURESPILL_MAGIC = 0x46534C42;

    /** vanilla .mca 청크를 읽어 ChunkColumn으로. OracleGroundtruthAuditTest.readVanillaChunk과 동일. */
    static ChunkColumn readVanillaChunk(Path worldDir, BlockRegistry reg, int cx, int cz) throws Exception {
        int rx = cx >> 5, rz = cz >> 5;
        Path regionFile = worldDir.resolve("region").resolve("r." + rx + "." + rz + ".mca");
        assertTrue(Files.exists(regionFile), "region file not found: " + regionFile);
        try (RegionFile rf = new RegionFile(regionFile)) {
            byte[] payload = rf.readChunk(cx, cz);
            assertNotNull(payload, "chunk not generated: " + cx + "," + cz);
            NbtCompound root = NbtIo.read(new DataInputStream(new ByteArrayInputStream(payload)));
            AnvilChunkCodec codec = new AnvilChunkCodec(reg, 0);
            return codec.fromNbt(root);
        }
    }

    /** FSLB fixture 바이트 직렬화: 헤더 20B + y/lz/lx 순 u16 LE stateId. */
    static byte[] writeFixtureBytes(long seed, int cx, int cz, ChunkColumn col) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(20 + 16 * 16 * HEIGHT * 2);
        ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(FEATURESPILL_MAGIC).putLong(seed).putInt(cx).putInt(cz);
        out.writeBytes(header.array());
        ByteBuffer block = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        for (int y = MIN_Y; y < MIN_Y + HEIGHT; y++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int lx = 0; lx < 16; lx++) {
                    block.clear();
                    block.putShort((short) col.getBlock(lx, y, lz));
                    out.writeBytes(block.array());
                }
            }
        }
        return out.toByteArray();
    }

    /** 이미 직렬화된 fixture 바이트를 파싱해 (seed,cx,cz,ids[])로 되돌린다 — 왕복검증용. */
    private record ParsedFixture(long seed, int cx, int cz, int[] ids) {}

    private static ParsedFixture parseFixtureBytes(byte[] fx) {
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FEATURESPILL_MAGIC, header.getInt(), "magic mismatch");
        long seed = header.getLong();
        int cx = header.getInt();
        int cz = header.getInt();
        int count = 16 * 16 * HEIGHT;
        int[] ids = new int[count];
        int pos = 20;
        for (int i = 0; i < count; i++) {
            ids[i] = ByteBuffer.wrap(fx, pos, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
            pos += 2;
        }
        return new ParsedFixture(seed, cx, cz, ids);
    }

    private static byte[] serializeIds(long seed, int cx, int cz, int[] ids) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(20 + ids.length * 2);
        ByteBuffer header = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        header.putInt(FEATURESPILL_MAGIC).putLong(seed).putInt(cx).putInt(cz);
        out.writeBytes(header.array());
        ByteBuffer block = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        for (int id : ids) {
            block.clear();
            block.putShort((short) id);
            out.writeBytes(block.array());
        }
        return out.toByteArray();
    }

    /**
     * Step 1 게이트 — 항상 실행(system property 불필요). 기존 fixture 하나(1234_0_0)를 읽어
     * 파싱→재직렬화한 뒤 원본 바이트와 완전히 동일한지 확인한다. 여기서 어긋나면 포맷 왕복
     * 로직 자체가 틀린 것이므로 이후 전량 재생성은 전부 무의미하다.
     */
    @Test void roundtrip_existingFixtureBytesUnchanged() throws Exception {
        String fixtureName = "/worldgen/fixtures/featurespill_1234_0_0.bin";
        byte[] original;
        try (InputStream in = FeatureSpillFixtureGenTool.class.getResourceAsStream(fixtureName)) {
            assertNotNull(in, "fixture not found: " + fixtureName);
            original = in.readAllBytes();
        }
        ParsedFixture parsed = parseFixtureBytes(original);
        byte[] roundTripped = serializeIds(parsed.seed(), parsed.cx(), parsed.cz(), parsed.ids());
        assertArrayEquals(original, roundTripped,
            "round-trip byte mismatch — FSLB 포맷 왕복 로직이 기존 fixture와 어긋남");
    }

    /**
     * 실행: -Dfurnace.fixture.gen.worldDir=<world dir> -Dfurnace.fixture.gen.seed=<seed>
     *      -Dfurnace.fixture.gen.coords="cx1:cz1,cx2:cz2,..." -Dfurnace.fixture.gen.outDir=<optional, 기본 src/test/resources/worldgen/fixtures>
     * 지정 좌표들을 vanilla .mca에서 읽어 featurespill_<seed>_<cx>_<cz>.bin으로 덮어쓴다.
     */
    @Test void generateFixturesFromVanillaWorld() throws Exception {
        String worldDirProp = System.getProperty("furnace.fixture.gen.worldDir");
        assumeTrue(worldDirProp != null, "furnace.fixture.gen.worldDir not set - skipping fixture generation");
        long seed = Long.parseLong(System.getProperty("furnace.fixture.gen.seed"));
        String coordsProp = System.getProperty("furnace.fixture.gen.coords");
        assertNotNull(coordsProp, "furnace.fixture.gen.coords must be set (cx:cz,cx:cz,...)");
        String outDirProp = System.getProperty("furnace.fixture.gen.outDir",
            "src/test/resources/worldgen/fixtures");

        Path worldDir = Paths.get(worldDirProp);
        Path outDir = Paths.get(outDirProp);
        Files.createDirectories(outDir);

        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");

        for (String spec : coordsProp.split(",")) {
            String[] p = spec.trim().split(":");
            assertEquals(2, p.length, "coord spec must be cx:cz but was: " + spec);
            int cx = Integer.parseInt(p[0]);
            int cz = Integer.parseInt(p[1]);

            ChunkColumn col = readVanillaChunk(worldDir, reg, cx, cz);
            byte[] fixture = writeFixtureBytes(seed, cx, cz, col);
            Path outFile = outDir.resolve("featurespill_" + seed + "_" + cx + "_" + cz + ".bin");
            Files.write(outFile, fixture);
            System.out.printf("FIXTURE_GEN wrote %s (%d bytes)%n", outFile, fixture.length);
        }
    }
}
