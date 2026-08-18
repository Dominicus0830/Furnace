// 광역 스윕 parity 테스트 — seed·중심·반경으로 N×N 청크를 순정 vanilla 실서버 .mca 기준 fixture와 일괄 대조한다.
//
// 기존 파리티 테스트는 청크 10개 안팎만 검증해, birch 나무·연못·버섯처럼 fixture에 없는 좌표의 회귀를
// 사용자 육안(실서버)이 먼저 발견하는 일이 있었다(task parity-verification-infra 배경). 이 테스트는 그
// 갭을 메우려는 광역 회귀 그물이며, 기본 suite에는 포함되지 않는다(opt-in, -Dfurnace.sweep=true 필요) —
// 289청크(반경 8) 기준 oracle 배치 실행에만 4~5분이 걸려 기본 suite 시간을 폭증시키기 때문이다.
//
// sweep-mca-migration(2026-08-17): 정답지를 oracle featurefull → 순정 vanilla 실서버 .mca featurespill로
// 전환했다. 이전에는 spill 완료된 Furnace 출력(generateWithFeatures)을 center-only decorate인 featurefull
// fixture와 비교해 의미론이 어긋나 있었고, 그 잔차가 baseline(청크당 1000~3800)에 그대로 박혀 있었다.
// 이제 양쪽 의미론이 일치하므로 baseline은 실제 발산만 담는다.
//
// 사용자용 실행법(oracle 배치 먼저 실행 → fixture 생성, 그 다음 테스트):
//   순정 vanilla 서버로 월드 생성(generate-structures=false + /tick freeze) 후
//   FeatureSpillFixtureGenTool로 fixture 생성 — tasks/sweep-mca-migration 참조
//   ./gradlew :furnace-server:test --tests "*ChunkSweepParityTest*" -Dfurnace.sweep=true
//   (다른 seed/중심/반경: -Dfurnace.sweep.seed=<seed> -Dfurnace.sweep.cx=<cx> -Dfurnace.sweep.cz=<cz> -Dfurnace.sweep.radius=<r>)
package org.domi.furnace.server.world.gen;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.BlockRegistry;
import org.domi.furnace.server.world.ChunkColumn;
import org.junit.jupiter.api.Test;

class ChunkSweepParityTest {

    private static final int MIN_Y             = -64;
    private static final int HEIGHT             = 384;
    private static final int FEATURESPILL_MAGIC = 0x46534C42;

    /** true면 baseline 비교 대신 실측치를 put 문 형태로 출력한다(-Dfurnace.sweep.recompute=true). */
    private static final boolean RECOMPUTE_BASELINE = Boolean.getBoolean("furnace.sweep.recompute");

    private record ChunkResult(int cx, int cz, int mismatch, boolean fixtureFound, List<String> samples) {}

    @Test
    void sweep_reportsPerChunkMismatchAgainstBaseline() throws Exception {
        assumeTrue(Boolean.getBoolean("furnace.sweep"),
            "opt-in 테스트 — -Dfurnace.sweep=true 로 실행할 것 (기본 suite에서는 skip)");

        long seed  = Long.getLong("furnace.sweep.seed", 51532L);
        int centerX = Integer.getInteger("furnace.sweep.cx", 0);
        int centerZ = Integer.getInteger("furnace.sweep.cz", 0);
        int radius  = Integer.getInteger("furnace.sweep.radius", 8);

        BlockRegistry reg = BlockRegistry.load("blocks/1.21.1.txt");
        Map<Integer, String> oracleIdToName = loadIdToName("/blocks/1.21.1.txt");
        RegistrySet registries = RegistrySet.load("registries/1.21.1.txt");
        NoiseWorldSource source = NoiseWorldSource.overworld(seed, reg, registries);

        int size = 2 * radius + 1;
        List<ChunkResult> results = new ArrayList<>(size * size);
        int missingFixtures = 0;
        for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
            for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
                results.add(compareChunk(source, reg, oracleIdToName, seed, cx, cz));
            }
        }
        for (ChunkResult r : results) if (!r.fixtureFound) missingFixtures++;

        printHeatmap(results, centerX, centerZ, radius);
        printTopMismatches(results);

        System.out.printf("sweep seed=%d center=(%d,%d) radius=%d: %d/%d fixture 발견, 총 mismatch=%d%n",
            seed, centerX, centerZ, radius, results.size() - missingFixtures, results.size(),
            results.stream().mapToInt(ChunkResult::mismatch).sum());

        assertEquals(0, missingFixtures,
            "fixture 누락 " + missingFixtures + "건 — 실서버 월드 생성 + FeatureSpillFixtureGenTool을 먼저 실행할 것 (클래스 헤더 참조)");

        // baseline: 반경 8, seed 51532, center(0,0) 실측(2026-07-06). 다른 파라미터로 실행 시 baseline이 없어
        // 회귀 감지 없이 리포트만 출력한다(정보성) — 새 기본 파라미터로 baseline을 갱신하려면 SWEEP_BASELINE_51532_0_0을
        // 직접 갱신할 것.
        if (RECOMPUTE_BASELINE) {
            System.out.println("=== BASELINE_RECOMPUTE (put 문 생성용) ===");
            for (ChunkResult r : results) {
                System.out.printf("put(%d,%d,%d);%n", r.cx(), r.cz(), r.mismatch());
            }
        } else if (seed == 51532L && centerX == 0 && centerZ == 0 && radius == 8) {
            for (ChunkResult r : results) {
                Integer baseline = SWEEP_BASELINE_51532_0_0.get(key(r.cx(), r.cz()));
                assertNotNull(baseline, "baseline 없음: (" + r.cx() + "," + r.cz() + ") — SWEEP_BASELINE_51532_0_0 갱신 필요");
                assertTrue(r.mismatch() <= baseline,
                    "(" + r.cx() + "," + r.cz() + ") mismatch=" + r.mismatch() + " > baseline=" + baseline + " — 회귀 의심");
            }
        } else {
            System.out.println("baseline 비교 skip (기본 파라미터가 아님) — 리포트만 참고할 것");
        }
    }

    private ChunkResult compareChunk(NoiseWorldSource source, BlockRegistry reg,
            Map<Integer, String> oracleIdToName, long seed, int cx, int cz) throws Exception {
        String fixtureName = "/worldgen/fixtures/featurespill_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = ChunkSweepParityTest.class.getResourceAsStream(fixtureName)) {
            if (in == null) return new ChunkResult(cx, cz, 0, false, List.of());
            fx = in.readAllBytes();
        }
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FEATURESPILL_MAGIC, header.getInt(), "magic mismatch cx=" + cx + " cz=" + cz);

        ChunkColumn col = source.generateWithFeatures(cx, cz);

        int mismatch = 0;
        List<String> samples = new ArrayList<>();
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
                        if (samples.size() < 5) {
                            samples.add(String.format("lx=%d y=%d lz=%d exp=%s(%d) got=%s(%d)",
                                lx, y, lz, name, expectedId, reg.nameOf(got), got));
                        }
                    }
                }
            }
        }
        return new ChunkResult(cx, cz, mismatch, true, samples);
    }

    /** 청크별 mismatch 카운트를 텍스트 히트맵(3자리 폭)으로 출력. 0은 '.'로 표시해 눈에 띄게 한다. */
    private void printHeatmap(List<ChunkResult> results, int centerX, int centerZ, int radius) {
        Map<Long, ChunkResult> byPos = new HashMap<>();
        for (ChunkResult r : results) byPos.put(key(r.cx(), r.cz()), r);

        System.out.println("=== mismatch 히트맵 (행=cz, 열=cx, 상단부터 cz 오름차순) ===");
        StringBuilder header = new StringBuilder("cz\\cx");
        for (int cx = centerX - radius; cx <= centerX + radius; cx++) header.append(String.format("%5d", cx));
        System.out.println(header);
        for (int cz = centerZ - radius; cz <= centerZ + radius; cz++) {
            StringBuilder row = new StringBuilder(String.format("%5d", cz));
            for (int cx = centerX - radius; cx <= centerX + radius; cx++) {
                ChunkResult r = byPos.get(key(cx, cz));
                if (r == null || !r.fixtureFound()) row.append("    ?");
                else row.append(r.mismatch() == 0 ? "    ." : String.format("%5d", r.mismatch()));
            }
            System.out.println(row);
        }
    }

    /** mismatch 상위 청크의 좌표·샘플 blockstate 쌍을 출력(가능하면 feature류 추정을 위한 원재료). */
    private void printTopMismatches(List<ChunkResult> results) {
        List<ChunkResult> sorted = new ArrayList<>(results);
        sorted.sort((a, b) -> Integer.compare(b.mismatch(), a.mismatch()));
        System.out.println("=== mismatch 상위 10개 청크 ===");
        for (int i = 0; i < Math.min(10, sorted.size()); i++) {
            ChunkResult r = sorted.get(i);
            if (r.mismatch() == 0) break;
            System.out.printf("(%d,%d) mismatch=%d samples=%s%n", r.cx(), r.cz(), r.mismatch(), r.samples());
        }
    }

    private static Long key(int cx, int cz) {
        return (((long) cx) << 32) ^ (cz & 0xFFFFFFFFL);
    }

    private static Map<Integer, String> loadIdToName(String resourcePath) throws Exception {
        Map<Integer, String> map = new HashMap<>();
        try (InputStream in = ChunkSweepParityTest.class.getResourceAsStream(resourcePath);
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

    // seed=51532 center=(0,0) radius=8 실측 baseline — sweep-mca-migration(2026-08-17)에서 순정 vanilla
    // 실서버 .mca 기준으로 재산출. 이전 값(총 516,471)은 spill 완료 출력을 center-only featurefull과
    // 비교한 의미론 불일치 잔차가 대부분이었고, 정답지 교체 후 총 46,789로 떨어졌다(-91%).
    // 청크당 평균 161.9 / 중앙값 51 / 최대 1,285 / 0인 청크 26개 (98,304 blocks 기준 평균 0.16%).
    // 이 값들은 "정답(0)"이 아니라 현재 실측치를 상한으로 고정한 회귀 감지선이다.
    // 갱신하려면 -Dfurnace.sweep.recompute=true 로 실행해 출력된 put 문으로 교체할 것.
    private static final Map<Long, Integer> SWEEP_BASELINE_51532_0_0 = new TreeMap<>();
    private static void put(int cx, int cz, int v) { SWEEP_BASELINE_51532_0_0.put(key(cx, cz), v); }
    static {
        put(-8,-8,552); put(-7,-8,126); put(-6,-8,94); put(-5,-8,39); put(-4,-8,24); put(-3,-8,233); put(-2,-8,30); put(-1,-8,7);
        put(0,-8,1); put(1,-8,231); put(2,-8,40); put(3,-8,59); put(4,-8,17); put(5,-8,20); put(6,-8,7); put(7,-8,74);
        put(8,-8,230); put(-8,-7,6); put(-7,-7,85); put(-6,-7,99); put(-5,-7,433); put(-4,-7,479); put(-3,-7,332); put(-2,-7,89);
        put(-1,-7,13); put(0,-7,9); put(1,-7,494); put(2,-7,7); put(3,-7,80); put(4,-7,25); put(5,-7,14); put(6,-7,48);
        put(7,-7,90); put(8,-7,93); put(-8,-6,7); put(-7,-6,6); put(-6,-6,27); put(-5,-6,774); put(-4,-6,639); put(-3,-6,102);
        put(-2,-6,716); put(-1,-6,402); put(0,-6,25); put(1,-6,224); put(2,-6,854); put(3,-6,501); put(4,-6,1); put(5,-6,0);
        put(6,-6,51); put(7,-6,286); put(8,-6,85); put(-8,-5,86); put(-7,-5,285); put(-6,-5,152); put(-5,-5,359); put(-4,-5,72);
        put(-3,-5,11); put(-2,-5,153); put(-1,-5,119); put(0,-5,9); put(1,-5,245); put(2,-5,806); put(3,-5,358); put(4,-5,145);
        put(5,-5,21); put(6,-5,14); put(7,-5,15); put(8,-5,6); put(-8,-4,33); put(-7,-4,9); put(-6,-4,14); put(-5,-4,520);
        put(-4,-4,852); put(-3,-4,179); put(-2,-4,0); put(-1,-4,168); put(0,-4,193); put(1,-4,381); put(2,-4,121); put(3,-4,107);
        put(4,-4,362); put(5,-4,444); put(6,-4,33); put(7,-4,28); put(8,-4,0); put(-8,-3,8); put(-7,-3,3); put(-6,-3,11);
        put(-5,-3,114); put(-4,-3,153); put(-3,-3,426); put(-2,-3,660); put(-1,-3,388); put(0,-3,153); put(1,-3,4); put(2,-3,13);
        put(3,-3,87); put(4,-3,9); put(5,-3,1); put(6,-3,79); put(7,-3,0); put(8,-3,15); put(-8,-2,0); put(-7,-2,14);
        put(-6,-2,15); put(-5,-2,21); put(-4,-2,113); put(-3,-2,39); put(-2,-2,1285); put(-1,-2,778); put(0,-2,713); put(1,-2,360);
        put(2,-2,291); put(3,-2,179); put(4,-2,749); put(5,-2,1117); put(6,-2,0); put(7,-2,30); put(8,-2,0); put(-8,-1,0);
        put(-7,-1,67); put(-6,-1,0); put(-5,-1,23); put(-4,-1,7); put(-3,-1,17); put(-2,-1,534); put(-1,-1,324); put(0,-1,484);
        put(1,-1,392); put(2,-1,324); put(3,-1,702); put(4,-1,58); put(5,-1,2); put(6,-1,6); put(7,-1,522); put(8,-1,57);
        put(-8,0,4); put(-7,0,5); put(-6,0,9); put(-5,0,363); put(-4,0,257); put(-3,0,420); put(-2,0,853); put(-1,0,643);
        put(0,0,234); put(1,0,315); put(2,0,409); put(3,0,10); put(4,0,17); put(5,0,2); put(6,0,207); put(7,0,657);
        put(8,0,168); put(-8,1,4); put(-7,1,2); put(-6,1,18); put(-5,1,75); put(-4,1,64); put(-3,1,273); put(-2,1,373);
        put(-1,1,307); put(0,1,232); put(1,1,231); put(2,1,225); put(3,1,798); put(4,1,937); put(5,1,98); put(6,1,12);
        put(7,1,63); put(8,1,69); put(-8,2,39); put(-7,2,16); put(-6,2,21); put(-5,2,317); put(-4,2,45); put(-3,2,65);
        put(-2,2,553); put(-1,2,394); put(0,2,254); put(1,2,307); put(2,2,260); put(3,2,42); put(4,2,25); put(5,2,486);
        put(6,2,25); put(7,2,0); put(8,2,22); put(-8,3,53); put(-7,3,135); put(-6,3,60); put(-5,3,387); put(-4,3,117);
        put(-3,3,28); put(-2,3,7); put(-1,3,5); put(0,3,74); put(1,3,0); put(2,3,0); put(3,3,0); put(4,3,6);
        put(5,3,171); put(6,3,49); put(7,3,584); put(8,3,38); put(-8,4,26); put(-7,4,0); put(-6,4,8); put(-5,4,318);
        put(-4,4,431); put(-3,4,22); put(-2,4,119); put(-1,4,139); put(0,4,180); put(1,4,3); put(2,4,41); put(3,4,25);
        put(4,4,200); put(5,4,35); put(6,4,3); put(7,4,207); put(8,4,32); put(-8,5,51); put(-7,5,23); put(-6,5,68);
        put(-5,5,17); put(-4,5,602); put(-3,5,117); put(-2,5,13); put(-1,5,0); put(0,5,133); put(1,5,0); put(2,5,482);
        put(3,5,15); put(4,5,126); put(5,5,0); put(6,5,1); put(7,5,54); put(8,5,412); put(-8,6,21); put(-7,6,50);
        put(-6,6,150); put(-5,6,49); put(-4,6,529); put(-3,6,37); put(-2,6,11); put(-1,6,0); put(0,6,0); put(1,6,0);
        put(2,6,8); put(3,6,16); put(4,6,3); put(5,6,2); put(6,6,0); put(7,6,8); put(8,6,231); put(-8,7,0);
        put(-7,7,12); put(-6,7,2); put(-5,7,69); put(-4,7,387); put(-3,7,21); put(-2,7,172); put(-1,7,118); put(0,7,0);
        put(1,7,6); put(2,7,1); put(3,7,0); put(4,7,1); put(5,7,0); put(6,7,20); put(7,7,22); put(8,7,4);
        put(-8,8,333); put(-7,8,51); put(-6,8,47); put(-5,8,21); put(-4,8,9); put(-3,8,132); put(-2,8,95); put(-1,8,63);
        put(0,8,47); put(1,8,25); put(2,8,20); put(3,8,29); put(4,8,0); put(5,8,22); put(6,8,460); put(7,8,102);
        put(8,8,14);
    }
}
