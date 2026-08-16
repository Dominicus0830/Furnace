// 광역 스윕 parity 테스트 — seed·중심·반경으로 N×N 청크를 oracle featurefull fixture와 일괄 대조한다.
//
// 기존 파리티 테스트는 청크 10개 안팎만 검증해, birch 나무·연못·버섯처럼 fixture에 없는 좌표의 회귀를
// 사용자 육안(실서버)이 먼저 발견하는 일이 있었다(task parity-verification-infra 배경). 이 테스트는 그
// 갭을 메우려는 광역 회귀 그물이며, 기본 suite에는 포함되지 않는다(opt-in, -Dfurnace.sweep=true 필요) —
// 289청크(반경 8) 기준 oracle 배치 실행에만 4~5분이 걸려 기본 suite 시간을 폭증시키기 때문이다.
//
// featurefull fixture는 center origin만 decorate하는 "반쪽 의미"라 청크 경계 spill 잔차가 항상 섞여
// 나온다(FeatureSpillParityTest가 별도로 담당) — 이 테스트는 그 잔차를 포함한 채로 청크별 mismatch 총량을
// baseline과 비교해 "새로운 대형 회귀"만 잡는다. mismatch를 0으로 만드는 것은 이 테스트의 목표가 아니다
// (brief 지시: 발견해도 고치지 말고 분해·기록만).
//
// 사용자용 실행법(oracle 배치 먼저 실행 → fixture 생성, 그 다음 테스트):
//   ./gradlew :worldgen-oracle:run --args="51532 featurefull:-8:-8 featurefull:-7:-8 ... featurefull:8:8"
//     (cx,cz 각각 -radius..radius 전체 조합, 기본 seed=51532 cx=0 cz=0 radius=8 → 17x17=289개)
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
    private static final int FEATUREFULL_MAGIC  = 0x46464C42;

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
            "fixture 누락 " + missingFixtures + "건 — oracle featurefull 배치를 먼저 실행할 것 (클래스 헤더 실행법 참조)");

        // baseline: 반경 8, seed 51532, center(0,0) 실측(2026-07-06). 다른 파라미터로 실행 시 baseline이 없어
        // 회귀 감지 없이 리포트만 출력한다(정보성) — 새 기본 파라미터로 baseline을 갱신하려면 SWEEP_BASELINE_51532_0_0을
        // 직접 갱신할 것.
        if (seed == 51532L && centerX == 0 && centerZ == 0 && radius == 8) {
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
        String fixtureName = "/worldgen/fixtures/featurefull_" + seed + "_" + cx + "_" + cz + ".bin";
        byte[] fx;
        try (InputStream in = ChunkSweepParityTest.class.getResourceAsStream(fixtureName)) {
            if (in == null) return new ChunkResult(cx, cz, 0, false, List.of());
            fx = in.readAllBytes();
        }
        ByteBuffer header = ByteBuffer.wrap(fx, 0, 20).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(FEATUREFULL_MAGIC, header.getInt(), "magic mismatch cx=" + cx + " cz=" + cz);

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

    // seed=51532 center=(0,0) radius=8 실측 baseline(2026-08-16 재실측, carver seed+i3 오프셋 수정 후,
    // 289청크 전체). featurefull은 center-only decorate라 청크 경계 spill 잔차가 항상 섞여 나온다(클래스
    // 헤더 참조) — 이 값은 "정답(0)"이 아니라 현재 실측치를 그대로 상한으로 고정한 회귀 감지선이다
    // (FeatureSpillParityTest의 border-tree 패턴과 동일). carver seed 오프셋 수정으로 carve 결과 자체가
    // 달라져 이전 baseline(2026-07-06)은 더 이상 유효하지 않다 — carver-seed-offset 태스크 참조.
    private static final Map<Long, Integer> SWEEP_BASELINE_51532_0_0 = new TreeMap<>();
    private static void put(int cx, int cz, int v) { SWEEP_BASELINE_51532_0_0.put(key(cx, cz), v); }
    static {
        put(-8,-8,1285); put(-7,-8,2168); put(-6,-8,1295); put(-5,-8,1132); put(-4,-8,926); put(-3,-8,1812); put(-2,-8,2517); put(-1,-8,2132);
        put(0,-8,954); put(1,-8,1970); put(2,-8,2155); put(3,-8,3530); put(4,-8,1130); put(5,-8,1170); put(6,-8,2038); put(7,-8,1400); put(8,-8,1362);
        put(-8,-7,1174); put(-7,-7,2082); put(-6,-7,1753); put(-5,-7,1369); put(-4,-7,2681); put(-3,-7,1051); put(-2,-7,1455); put(-1,-7,2762);
        put(0,-7,1132); put(1,-7,1799); put(2,-7,2103); put(3,-7,1896); put(4,-7,1028); put(5,-7,2853); put(6,-7,2818); put(7,-7,1134); put(8,-7,1801);
        put(-8,-6,1584); put(-7,-6,1152); put(-6,-6,1500); put(-5,-6,2661); put(-4,-6,3427); put(-3,-6,1433); put(-2,-6,2004); put(-1,-6,1437);
        put(0,-6,1892); put(1,-6,2323); put(2,-6,1430); put(3,-6,2200); put(4,-6,1379); put(5,-6,1599); put(6,-6,1555); put(7,-6,1615); put(8,-6,1599);
        put(-8,-5,1695); put(-7,-5,2347); put(-6,-5,1980); put(-5,-5,2217); put(-4,-5,1184); put(-3,-5,1639); put(-2,-5,2270); put(-1,-5,2013);
        put(0,-5,1708); put(1,-5,2673); put(2,-5,1925); put(3,-5,2312); put(4,-5,2530); put(5,-5,2584); put(6,-5,910); put(7,-5,1054); put(8,-5,1562);
        put(-8,-4,2219); put(-7,-4,1661); put(-6,-4,1761); put(-5,-4,1749); put(-4,-4,1004); put(-3,-4,648); put(-2,-4,669); put(-1,-4,1177);
        put(0,-4,1258); put(1,-4,1313); put(2,-4,1756); put(3,-4,1005); put(4,-4,1996); put(5,-4,1791); put(6,-4,2171); put(7,-4,1936); put(8,-4,1643);
        put(-8,-3,1153); put(-7,-3,1675); put(-6,-3,1804); put(-5,-3,2458); put(-4,-3,2154); put(-3,-3,2454); put(-2,-3,1996); put(-1,-3,1786);
        put(0,-3,1275); put(1,-3,1198); put(2,-3,1346); put(3,-3,2048); put(4,-3,3042); put(5,-3,1799); put(6,-3,1774); put(7,-3,1454); put(8,-3,2844);
        put(-8,-2,1259); put(-7,-2,1927); put(-6,-2,1094); put(-5,-2,1534); put(-4,-2,1759); put(-3,-2,1953); put(-2,-2,1450); put(-1,-2,987);
        put(0,-2,2036); put(1,-2,1875); put(2,-2,2343); put(3,-2,1907); put(4,-2,1692); put(5,-2,2493); put(6,-2,1463); put(7,-2,1853); put(8,-2,1268);
        put(-8,-1,2115); put(-7,-1,1349); put(-6,-1,2391); put(-5,-1,1318); put(-4,-1,1449); put(-3,-1,783); put(-2,-1,784); put(-1,-1,878);
        put(0,-1,1920); put(1,-1,1718); put(2,-1,1454); put(3,-1,1994); put(4,-1,1629); put(5,-1,2116); put(6,-1,2169); put(7,-1,1883); put(8,-1,918);
        put(-8,0,2170); put(-7,0,930); put(-6,0,1852); put(-5,0,1998); put(-4,0,1672); put(-3,0,2630); put(-2,0,1480); put(-1,0,2362);
        put(0,0,3394); put(1,0,2328); put(2,0,1865); put(3,0,2277); put(4,0,2598); put(5,0,2670); put(6,0,2365); put(7,0,2071); put(8,0,1103);
        put(-8,1,1248); put(-7,1,2008); put(-6,1,1014); put(-5,1,1070); put(-4,1,1210); put(-3,1,1983); put(-2,1,2054); put(-1,1,1447);
        put(0,1,1334); put(1,1,1640); put(2,1,1778); put(3,1,3815); put(4,1,2314); put(5,1,1498); put(6,1,1388); put(7,1,1732); put(8,1,1377);
        put(-8,2,1425); put(-7,2,2438); put(-6,2,1644); put(-5,2,1497); put(-4,2,2125); put(-3,2,1727); put(-2,2,2165); put(-1,2,937);
        put(0,2,1507); put(1,2,1471); put(2,2,2587); put(3,2,2524); put(4,2,2118); put(5,2,1060); put(6,2,2162); put(7,2,2485); put(8,2,1577);
        put(-8,3,1769); put(-7,3,3003); put(-6,3,1553); put(-5,3,913); put(-4,3,1874); put(-3,3,1077); put(-2,3,1479); put(-1,3,1667);
        put(0,3,865); put(1,3,1392); put(2,3,2012); put(3,3,1391); put(4,3,2334); put(5,3,1581); put(6,3,1530); put(7,3,2508); put(8,3,2150);
        put(-8,4,910); put(-7,4,1967); put(-6,4,1855); put(-5,4,1730); put(-4,4,2286); put(-3,4,1168); put(-2,4,828); put(-1,4,1480);
        put(0,4,2337); put(1,4,1346); put(2,4,1665); put(3,4,1232); put(4,4,1494); put(5,4,2078); put(6,4,1032); put(7,4,1850); put(8,4,1947);
        put(-8,5,2031); put(-7,5,1802); put(-6,5,1387); put(-5,5,2508); put(-4,5,1921); put(-3,5,1389); put(-2,5,1616); put(-1,5,1546);
        put(0,5,1326); put(1,5,2939); put(2,5,2454); put(3,5,1590); put(4,5,1345); put(5,5,1129); put(6,5,891); put(7,5,1732); put(8,5,1537);
        put(-8,6,1891); put(-7,6,2795); put(-6,6,2290); put(-5,6,1791); put(-4,6,2003); put(-3,6,1840); put(-2,6,1651); put(-1,6,1262);
        put(0,6,1905); put(1,6,1851); put(2,6,1798); put(3,6,2158); put(4,6,1324); put(5,6,1343); put(6,6,1945); put(7,6,2537); put(8,6,1730);
        put(-8,7,1575); put(-7,7,1692); put(-6,7,1603); put(-5,7,2608); put(-4,7,894); put(-3,7,1098); put(-2,7,2532); put(-1,7,1030);
        put(0,7,2101); put(1,7,1529); put(2,7,2025); put(3,7,1720); put(4,7,1057); put(5,7,2005); put(6,7,1163); put(7,7,2031); put(8,7,1884);
        put(-8,8,858); put(-7,8,2119); put(-6,8,1659); put(-5,8,1904); put(-4,8,1731); put(-3,8,2798); put(-2,8,2451); put(-1,8,1746);
        put(0,8,1134); put(1,8,2378); put(2,8,2844); put(3,8,1331); put(4,8,1518); put(5,8,1772); put(6,8,2084); put(7,8,1800); put(8,8,1099);
    }
}
