// featurespill parity 테스트 — ChunkPipeline이 9개 origin의 decoration spill(나무 캐노피·ore 등)을
// 전부 반영하는지 oracle featurespill fixture와 byte-exact 비교한다.
//
// featurefull fixture는 center origin만 decorate한 "반쪽 의미"라 FeaturePipelineParityTest는 실제로
// 이웃 origin spill을 검증하지 못한 채 green이었다(테스트 설계 구멍, task feature-neighbor-spill 참조).
// featurespill fixture는 5×5 carved grid에서 3×3 origin 9개를 전부 decorate한 뒤 center만 덤프하므로,
// 이 테스트가 green이어야 실서버 청크 경계의 spill 누락 버그가 실제로 고쳐졌다는 증거가 된다.
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class FeatureSpillParityTest {

    private static final int MIN_Y              = -64;
    private static final int HEIGHT             = 384;
    private static final int FEATURESPILL_MAGIC = 0x46534C42;

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = FeatureSpillParityTest.class.getResourceAsStream(resourcePath);
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

    private void assertSpillParity(long seed, int cx, int cz) throws Exception {
        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");

        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);
        ChunkColumn col = source.generateWithFeatures(cx, cz);

        String fixtureName = "/worldgen/fixtures/featurespill_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = FeatureSpillParityTest.class.getResourceAsStream(fixtureName)) {
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
                                "SPILL_MM seed=%d cx=%d cz=%d lx=%d y=%d lz=%d oracle=%d(%s) exp=%d got=%d%n",
                                seed, cx, cz, lx, y, lz, oracleId, name, expectedId, got);
                        }
                    }
                }
            }
        }

        System.out.printf("featurespill seed=%d cx=%d cz=%d: %d mismatches%n", seed, cx, cz, mismatch);
        assertEquals(0, mismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz + " mismatches=" + mismatch);
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    @Test void spill_seed1234_coord1() throws Exception { assertSpillParity(1234L, 0, 0); }
    @Test void spill_seed1234_coord2() throws Exception { assertSpillParity(1234L, -5, 3); }
    @Test void spill_seed1234_coord3() throws Exception { assertSpillParity(1234L, 7, -2); }

    /**
     * (-49,-20): 나무 몸통이 청크 경계(lx/lz 0 또는 15)를 넘어 이웃 origin 쪽으로 걸치는 border-tree 좌표.
     * 0 mismatch를 assert하지 않는다 — 이 좌표(및 인근 산림 지역 전반)는 TreeFeatureParityDiagnosticTest가
     * 이미 진단 전용(assert 없음)으로 남겨둔 것과 같은, tree feature 자체의 기존 parity 격차(이 task 이전부터
     * 존재, git stash로 ChunkPipeline만 되돌려 확인 — 원래 center-only 파이프라인도 동일 지역에서 mismatch가
     * 났다)와 섞여 나온다. 대신 이 테스트는 "9-origin spill 적용 전(1882) → 적용 후(738) mismatch 대폭 감소"를
     * 정량적으로 검증해 spill 수정이 방향적으로 옳음을 증명한다. 나무 fixture 자체의 완전한 byte-exact는
     * 별도 트리 기능 파리티 작업(project memory: 남음 M5·M6) 몫이다.
     */
    // round13: TreeFeature.Direction enum 선언 순서를 vanilla net.minecraft.core.Direction.values()
    // (DOWN,UP,NORTH,SOUTH,WEST,EAST)와 일치시켜 leaf-distance BFS의 이웃 순회·enqueue 순서가 일치하도록
    // 수정 — 남은 1건(patch_grass 아님, birch_leaves distance 프로퍼티 불일치)이 해소되어 79→1→0 byte-exact 도달.
    @Test void spill_seed1234_borderTree_improves() throws Exception {
        assertSpillParity(1234L, -49, -20);
    }

    /**
     * seed 51532 origin(5,7): round7~8에서 발견된 「origin (5,7) 자신의 birch_tall candidate 4가
     * WouldSurviveFilter에서 below=birch_leaves로 실패, oracle은 통과」 스필 경로 — round9에서 근본 원인을
     * ChunkColumn.surface[](WORLD_SURFACE_WG heightmap)가 feature decoration 중 setBlock 시 라이브 갱신되지
     * 않던 버그로 특정, 수정(updateSurface 추가)함. 이 fixture는 364→357로만 개선되어(같은 origin·같은
     * 청크 내 또 다른 잔여 tree-spill 경로가 남아있음) 0을 assert하지 않는다 — 상한만 조여 회귀를 방지한다.
     */
    // round13: patch_grass_forest의 WORLD_SURFACE_WG heightmap 조회가 carve 완료 이후에도 decoration 중
    // (이웃 origin의 나무 캐노피 등에) 계속 라이브 갱신되던 버그 수정 — ChunkColumn에 surfaceWg[](wgOceanFloor와
    // 동일 패턴, freeze 이후 no-op) 분리 도입, WORLD_SURFACE_WG 타입 heightmap 질의만 이 얼어붙은 값을 쓰도록
    // PlacementModifier·FeatureWorldView 분기 — 231→7→0 byte-exact 도달.
    @Test void spill_seed51532_5_7_improves() throws Exception {
        assertSpillParity(51532L, 5, 7);
    }

    /**
     * fancy_oak(fancy_trunk_placer + fancy_foliage_placer)가 실제로 배치되는 좌표 — 이전에는
     * fancy_trunk_placer/fancy_foliage_placer가 미구현이라 BiomeFeatureData의 default 분기(straight_trunk_placer
     * + blob_foliage_placer)로 대체 생성되던 지점(사용자 실서버 제보: 큰 나무만 이상 생성). 두 placer를
     * 새로 포팅하고 BiomeFeatureData에 연결한 뒤 0 mismatch 도달.
     */
    @Test void spill_seed51532_1_0_fancyOak() throws Exception {
        assertSpillParity(51532L, 1, 0);
    }
}
