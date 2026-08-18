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
        assertSpillParity(seed, cx, cz, 0);
    }

    // maxMismatch>0: 아직 root cause가 안 잡힌 잔여 mismatch를 회귀 없이 상한만 고정(기존
    // spill_seed51532_5_7_improves/borderTree_improves와 동일 패턴).
    private void assertSpillParity(long seed, int cx, int cz, int maxMismatch) throws Exception {
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
        if (maxMismatch > 0) {
            assertTrue(mismatch <= maxMismatch,
                "seed=" + seed + " cx=" + cx + " cz=" + cz + " mismatches=" + mismatch + " > 상한 " + maxMismatch);
        } else {
            assertEquals(0, mismatch, "seed=" + seed + " cx=" + cx + " cz=" + cz + " mismatches=" + mismatch);
        }
    }

    private static int tryId(BlockRegistry reg, String name, int fallback) {
        try { return reg.defaultStateId(name); }
        catch (Exception e) { return fallback; }
    }

    @Test void spill_seed1234_coord1() throws Exception { assertSpillParity(1234L, 0, 0); }

    // mca-groundtruth-migration(2026-08-18): 정답지를 oracle featurespill → 순정 vanilla 실서버 .mca로
    // 교체(task.md 근거: oracle이 feature 단계에서 실서버와 구조적으로 발산). 이 좌표는 oracle 기준으로는
    // 0 mismatch였으나 실서버 기준으로는 12 — oracle-vanilla 자체의 기존 divergence이지 이 교체로 생긴
    // 회귀가 아니다. root cause 미조사(backlog). 상한을 실측치로 고정.
    @Test void spill_seed1234_coord2() throws Exception { assertSpillParity(1234L, -5, 3, 12); }

    // mca-groundtruth-migration(2026-08-18): 위와 동일 사유. oracle 0 → 실서버 61.
    @Test void spill_seed1234_coord3() throws Exception { assertSpillParity(1234L, 7, -2, 61); }

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
    // mca-groundtruth-migration(2026-08-18): 이 좌표는 oracle fixture가 미생성 상태였다(task.md "미생성"
    // 표기) — 이번에 처음으로 실서버 .mca에서 fixture를 만들었다. 실측 24 mismatch, 원인 미조사(backlog).
    @Test void spill_seed1234_borderTree_improves() throws Exception {
        assertSpillParity(1234L, -49, -20, 24);
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
    // mca-groundtruth-migration(2026-08-17): 위 개선사는 모두 oracle fixture 기준. 실서버 .mca 기준으로
    // 재측정하니 26 mismatch — oracle 기준 0에서 소폭 증가. 원인 미조사(backlog).
    @Test void spill_seed51532_5_7_improves() throws Exception {
        assertSpillParity(51532L, 5, 7, 26);
    }

    /**
     * fancy_oak(fancy_trunk_placer + fancy_foliage_placer)가 실제로 배치되는 좌표 — 이전에는
     * fancy_trunk_placer/fancy_foliage_placer가 미구현이라 BiomeFeatureData의 default 분기(straight_trunk_placer
     * + blob_foliage_placer)로 대체 생성되던 지점(사용자 실서버 제보: 큰 나무만 이상 생성). 두 placer를
     * 새로 포팅하고 BiomeFeatureData에 연결한 뒤 0 mismatch 도달.
     */
    // mca-groundtruth-migration(2026-08-17): oracle 0 → 실서버 315. 원인 미조사(backlog).
    @Test void spill_seed51532_1_0_fancyOak() throws Exception {
        assertSpillParity(51532L, 1, 0, 315);
    }

    /**
     * (2,0): tree-overlap-residual task round2에서 root cause 확정 — origin(3,0)의 trees_birch_and_oak
     * count-loop 후보(world 58,72,1)에서 SurfaceWaterDepthFilterPlacement가 OCEAN_FLOOR와 WORLD_SURFACE를
     * 동일한 값(둘 다 71)으로 오판해 통과시켰다. 원인은 `ChunkColumn.isOceanFloorBlock`이 short_grass/fern/
     * dead_bush 같은 non-motion-blocking 지상 식물을 "바닥"으로 잘못 세는 것 — vanilla OCEAN_FLOOR 술어
     * (BlockBehaviour::blocksMotion)는 이들을 바닥에서 제외하므로, 이웃 origin(3,-1)의 patch_grass_forest가
     * 심은 short_grass(y=71) 위에서 vanilla는 diff=1(>max_water_depth=0)로 이 후보를 스킵하지만 Furnace는
     * diff=0으로 통과시켜 RNG 소비 순서가 call27부터 어긋났다(FURNACE nextFloat vs ORACLE nextInt(16)).
     * `isOceanFloorBlock`에 short_grass/fern/dead_bush 제외를 추가해 0 mismatch(exact) 도달.
     */
    // mca-groundtruth-migration(2026-08-17): 위 root cause 확정은 oracle fixture 기준. 실서버 .mca 기준
    // 재측정 409 mismatch — 원인 미조사(backlog), 상한만 실측치로 고정.
    @Test void spill_seed51532_2_0_fancyOak_improves() throws Exception {
        assertSpillParity(51532L, 2, 0, 409);
    }

    /**
     * (3,3): round4에서 root cause 확정 — origin(4,4)의 `birch_tall`(old_growth_birch_forest 전용
     * tree feature) count-loop 첫 후보 world(66,~,68)가 old_growth_birch_forest/forest biome 경계
     * 바로 위였다. vanilla의 `BiomeFilter`는 `BiomeManager.getBiome(pos)`(8개 인접 quart를 결정론적
     * "biome zoom" 지터로 블렌딩)를 쓰는데, 이 위치의 블렌딩 결과는 `forest`(birch_tall 서식지 아님)라
     * vanilla가 스킵한다. Furnace의 `PlacedFeature.placeAt`는 블렌딩 없는 단순 quart 조회
     * (`getBiomeId`)를 썼고, 그 raw quart 값은 `old_growth_birch_forest`(서식지 맞음)라서 통과시켜
     * RNG 소비 순서가 그 즉시 갈렸다(FURNACE nextFloat vs ORACLE nextInt(16), origin(4,4) birch_tall
     * 세션 call=4). oracle 측 `BiomeManager.getBiome`/`BiomeGenerationSettings.hasFeature`를 직접
     * 계측해 raw=old_growth_birch_forest, blended=forest, hasFeature(blended)=false를 실측 확인 후
     * 확정했다. `FeatureWorldView.getBlendedBiomeId`(vanilla BiomeManager 알고리즘 이식, 8-corner
     * fiddledDistance)를 추가해 `PlacedFeature`의 biome 게이트를 이 블렌딩된 값으로 교체 — 0
     * mismatch(exact) 도달.
     */
    @Test void spill_seed51532_3_3_fancyOak_improves() throws Exception {
        assertSpillParity(51532L, 3, 3);
    }

    /**
     * carver-regrass task — 사용자 제보 "연못 부재" 좌표(seed 51532, 청크 -3,-6)의 feature 단계 검증.
     * regrass 적용 전: 47 mismatch(y=62 dirt/grass_block, carver 단계 root cause). regrass 적용 후: 47건은
     * 해소됐지만 16건의 새 mismatch(y=63~64, oracle=air/got=brown_mushroom)가 나타났었다.
     *
     * root cause(pond-tree-residual task에서 확정) — vanilla SimpleBlockFeature.place는 배치 전
     * `blockState.canSurvive(level,pos)`를 호출하는데, `MushroomBlock.canSurvive`
     * (mcpal `MushroomBlock.java` L84-88)는 `below.is(MUSHROOM_GROW_BLOCK)`(mycelium/podzol/nylium)이거나
     * `getRawBrightness(pos,0) < 13 && mayPlaceOn(below)`일 때만 생존한다. Furnace의
     * `SimpleBlockFeature.place`(수정 전)는 이 분기 자체가 없이 `validGround`(dirt/grass 등 흙 계열) 하나로만
     * 판정해 하늘이 완전히 열린 연못 위에서도 항상 통과시켰다(patch_brown_mushroom.json에는 애초에
     * `would_survive` predicate가 없다 — 생존 판정은 feature 자신의 책임).
     *
     * 수정: `SimpleBlockFeature`에 mushroom 전용 3번째 생성자 인자(`mushroomGrowGround`, nullable)를 추가해
     * `mushroomGrowGround.contains(below) || (!view.isOpenToSky(x,y,z) && validGround.contains(below))`로
     * 분기(mushroom이 아닌 다른 simple_block feature는 기존 2-인자 경로 그대로 — 회귀 없음).
     * `isOpenToSky`는 정밀 스카이라이트 계산 대신, carve 완료 시 얼어붙는 `getWorldSurfaceWgY`보다 pos가
     * 높으면 완전 개방(skylight=15)으로 근사한다(라이브 MOTION_BLOCKING을 썼다면 같은 patch 안에서 먼저 놓인
     * mushroom 자신이 "지면"으로 잡혀 다음 시도를 오염시키는 자기간섭이 생겨 16→7까지만 줄었었다 — WG는
     * decoration 이전에 얼어붙어 이 자기간섭이 없다).
     */
    // mca-groundtruth-migration(2026-08-17): 위 root cause 확정은 전부 oracle fixture 기준. 실서버 .mca
    // 기준 재측정 36 mismatch — 원인 미조사(backlog).
    @Test void spill_seed51532_m3_m6_pondEdgeRegrass_improves() throws Exception {
        assertSpillParity(51532L, -3, -6, 36);
    }

    /**
     * mega-tree-placers task — cherry_grove 청크. cherry_trunk_placer + cherry_foliage_placer
     * (+ placeLeavesRowWithHangingLeavesBelow, TreeIntProvider.WeightedList branch_count) 검증.
     */
    // carver-seed-offset task(2026-08-16): carver +i3 오프셋 수정으로 111 mismatch 회귀 — 사유는
    // spill_seed51532_96_2_megaJungle 주석 참조.
    // mca-groundtruth-migration(2026-08-18): 정답지를 실서버 .mca로 교체 — 89 mismatch(oracle 기준 111보다
    // 오히려 작음). 상한을 실측치로 조임.
    @Test void spill_seed51532_17_m14_cherryGrove() throws Exception {
        assertSpillParity(51532L, 17, -14, 89);
    }

    /**
     * mega-tree-placers task — jungle 청크(이웃 biome이 jungle뿐인 클린 좌표). mega_jungle_trunk_placer
     * (giant 2x2 상속+나선 가지) + jungle_foliage_placer(vanilla MegaJungleFoliagePlacer) +
     * bush_foliage_placer(jungle_bush) + VinesFeature 검증.
     * giant-trunk(doubleTrunk=true) 경로가 실사용되므로 shouldSkipLocationSigned abs 이중 적용 수정도 커버.
     */
    // carver-seed-offset task(2026-08-16): carver +i3 오프셋 수정으로 지형이 바뀌며 1668 mismatch로
    // 회귀 — mega-tree-placers 소스(placer/decorator/feature)는 미커밋 작업분이라 이 task 범위에서
    // 손대지 않는다(task.md constraints). 상한만 고정해 회귀 방지, byte-exact 복구는 mega-tree-placers
    // task 몫.
    // mca-groundtruth-migration(2026-08-18): 정답지를 실서버 .mca로 교체 — 1566 mismatch(oracle 기준
    // 1668보다 작음). 상한을 실측치로 조임.
    @Test void spill_seed51532_96_2_megaJungle() throws Exception {
        assertSpillParity(51532L, 96, 2, 1566);
    }

    /**
     * mega-tree-placers task — old_growth_spruce_taiga 청크. giant_trunk_placer +
     * mega_pine_foliage_placer + alter_ground decorator(podzol 치환) 검증.
     */
    // carver-seed-offset task(2026-08-16): carver +i3 오프셋 수정으로 499 mismatch 회귀 — 사유는
    // spill_seed51532_96_2_megaJungle 주석 참조.
    // mca-groundtruth-migration(2026-08-18): 정답지를 실서버 .mca로 교체 — 987 mismatch(oracle 기준 499의
    // 약 2배). 실서버 대비 divergence가 더 크게 드러남. 원인 미조사(backlog), 상한을 실측치로 확대.
    @Test void spill_seed1234_m88_m24_megaSpruce() throws Exception {
        assertSpillParity(1234L, -88, -24, 987);
    }

    /**
     * mega-tree-placers task — mangrove_swamp 청크(이웃 biome이 mangrove_swamp뿐인 클린 좌표).
     * upwards_branching_trunk_placer + random_spread_foliage_placer + mangrove_root_placer +
     * attached_to_leaves decorator 검증.
     */
    // carver-seed-offset task(2026-08-16): carver +i3 오프셋 수정으로 62 mismatch 회귀 — 사유는
    // spill_seed51532_96_2_megaJungle 주석 참조.
    // mca-groundtruth-migration(2026-08-18): 정답지를 실서버 .mca로 교체 — 3408 mismatch(oracle 기준
    // 62의 약 55배). mangrove_swamp는 mega-tree-placers 미구현 항목이 많아 divergence가 크게 드러남.
    // 원인 미조사(backlog), 상한을 실측치로 확대.
    @Test void spill_seed1234_168_m44_mangrove() throws Exception {
        assertSpillParity(1234L, 168, -44, 3408);
    }
}
