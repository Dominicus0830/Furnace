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

    // seed=51532 center=(0,0) radius=8 실측 baseline(2026-07-06, 289청크 전체). featurefull은 center-only
    // decorate라 청크 경계 spill 잔차가 항상 섞여 나온다(클래스 헤더 참조) — 이 값은 "정답(0)"이 아니라
    // 현재 실측치를 그대로 상한으로 고정한 회귀 감지선이다(FeatureSpillParityTest의 border-tree 패턴과 동일).
    private static final Map<Long, Integer> SWEEP_BASELINE_51532_0_0 = new TreeMap<>();
    private static void put(int cx, int cz, int v) { SWEEP_BASELINE_51532_0_0.put(key(cx, cz), v); }
    static {
        put(-8,-8,1285); put(-7,-8,2389); put(-6,-8,1202); put(-5,-8,997); put(-4,-8,1117); put(-3,-8,1849); put(-2,-8,2514); put(-1,-8,2096);
        put(0,-8,956); put(1,-8,1970); put(2,-8,2155); put(3,-8,3614); put(4,-8,1968); put(5,-8,1552); put(6,-8,2316); put(7,-8,1457);
        put(8,-8,1371); put(-8,-7,1174); put(-7,-7,2074); put(-6,-7,1743); put(-5,-7,1370); put(-4,-7,2618); put(-3,-7,922); put(-2,-7,1394);
        put(-1,-7,2802); put(0,-7,1132); put(1,-7,1799); put(2,-7,2103); put(3,-7,1925); put(4,-7,1024); put(5,-7,3039); put(6,-7,2866);
        put(7,-7,1206); put(8,-7,1823); put(-8,-6,1582); put(-7,-6,1154); put(-6,-6,1510); put(-5,-6,2636); put(-4,-6,3461); put(-3,-6,1459);
        put(-2,-6,2000); put(-1,-6,1437); put(0,-6,1892); put(1,-6,2347); put(2,-6,1430); put(3,-6,2210); put(4,-6,1379); put(5,-6,1677);
        put(6,-6,1618); put(7,-6,1764); put(8,-6,1601); put(-8,-5,1693); put(-7,-5,2361); put(-6,-5,1971); put(-5,-5,2215); put(-4,-5,1246);
        put(-3,-5,1551); put(-2,-5,2223); put(-1,-5,2020); put(0,-5,1708); put(1,-5,2675); put(2,-5,1945); put(3,-5,2312); put(4,-5,2530);
        put(5,-5,2600); put(6,-5,951); put(7,-5,1081); put(8,-5,1482); put(-8,-4,2074); put(-7,-4,1622); put(-6,-4,1692); put(-5,-4,1692);
        put(-4,-4,998); put(-3,-4,707); put(-2,-4,699); put(-1,-4,1189); put(0,-4,1258); put(1,-4,1319); put(2,-4,1846); put(3,-4,1005);
        put(4,-4,1996); put(5,-4,1803); put(6,-4,2171); put(7,-4,1932); put(8,-4,1643); put(-8,-3,1150); put(-7,-3,1496); put(-6,-3,1651);
        put(-5,-3,2455); put(-4,-3,2155); put(-3,-3,2500); put(-2,-3,2196); put(-1,-3,1786); put(0,-3,1276); put(1,-3,1210); put(2,-3,1352);
        put(3,-3,2052); put(4,-3,3042); put(5,-3,1796); put(6,-3,1774); put(7,-3,1454); put(8,-3,2844); put(-8,-2,1254); put(-7,-2,1883);
        put(-6,-2,1079); put(-5,-2,1534); put(-4,-2,1759); put(-3,-2,1953); put(-2,-2,1575); put(-1,-2,986); put(0,-2,2041); put(1,-2,1875);
        put(2,-2,2333); put(3,-2,1899); put(4,-2,1698); put(5,-2,2387); put(6,-2,1512); put(7,-2,1853); put(8,-2,1268); put(-8,-1,2135);
        put(-7,-1,1345); put(-6,-1,2391); put(-5,-1,1315); put(-4,-1,1444); put(-3,-1,783); put(-2,-1,786); put(-1,-1,888); put(0,-1,1920);
        put(1,-1,1768); put(2,-1,1454); put(3,-1,1994); put(4,-1,1629); put(5,-1,2111); put(6,-1,2130); put(7,-1,1894); put(8,-1,918);
        put(-8,0,2204); put(-7,0,950); put(-6,0,1846); put(-5,0,1992); put(-4,0,1815); put(-3,0,2641); put(-2,0,1566); put(-1,0,2485);
        put(0,0,3416); put(1,0,2415); put(2,0,1867); put(3,0,2277); put(4,0,2598); put(5,0,2670); put(6,0,2292); put(7,0,1915);
        put(8,0,1103); put(-8,1,1248); put(-7,1,2003); put(-6,1,1010); put(-5,1,1068); put(-4,1,1208); put(-3,1,1835); put(-2,1,2078);
        put(-1,1,1458); put(0,1,1442); put(1,1,1640); put(2,1,1802); put(3,1,3815); put(4,1,2314); put(5,1,1498); put(6,1,1357);
        put(7,1,1732); put(8,1,1377); put(-8,2,1427); put(-7,2,2438); put(-6,2,1643); put(-5,2,1530); put(-4,2,2123); put(-3,2,1750);
        put(-2,2,2348); put(-1,2,980); put(0,2,1548); put(1,2,1487); put(2,2,2648); put(3,2,2547); put(4,2,2142); put(5,2,1051);
        put(6,2,2044); put(7,2,2466); put(8,2,1577); put(-8,3,1813); put(-7,3,3003); put(-6,3,1567); put(-5,3,904); put(-4,3,1936);
        put(-3,3,1128); put(-2,3,1615); put(-1,3,2046); put(0,3,1018); put(1,3,1537); put(2,3,2234); put(3,3,1668); put(4,3,2415);
        put(5,3,1921); put(6,3,1508); put(7,3,2503); put(8,3,2150); put(-8,4,915); put(-7,4,1933); put(-6,4,1855); put(-5,4,1732);
        put(-4,4,2456); put(-3,4,1350); put(-2,4,909); put(-1,4,1494); put(0,4,2568); put(1,4,1592); put(2,4,1685); put(3,4,1235);
        put(4,4,1481); put(5,4,2056); put(6,4,1030); put(7,4,1851); put(8,4,2044); put(-8,5,1922); put(-7,5,1746); put(-6,5,1363);
        put(-5,5,2534); put(-4,5,2026); put(-3,5,1462); put(-2,5,1890); put(-1,5,1600); put(0,5,1429); put(1,5,2988); put(2,5,2471);
        put(3,5,1623); put(4,5,1339); put(5,5,1223); put(6,5,1045); put(7,5,1836); put(8,5,1704); put(-8,6,1832); put(-7,6,2772);
        put(-6,6,2260); put(-5,6,1849); put(-4,6,2057); put(-3,6,1901); put(-2,6,1767); put(-1,6,1456); put(0,6,1922); put(1,6,1853);
        put(2,6,1768); put(3,6,2276); put(4,6,1208); put(5,6,1188); put(6,6,1811); put(7,6,2536); put(8,6,1730); put(-8,7,1507);
        put(-7,7,1684); put(-6,7,1589); put(-5,7,2608); put(-4,7,904); put(-3,7,1098); put(-2,7,2532); put(-1,7,1126); put(0,7,2140);
        put(1,7,1532); put(2,7,2024); put(3,7,1647); put(4,7,1012); put(5,7,1791); put(6,7,1101); put(7,7,2026); put(8,7,1884);
        put(-8,8,877); put(-7,8,2057); put(-6,8,1710); put(-5,8,1900); put(-4,8,1725); put(-3,8,2777); put(-2,8,2451); put(-1,8,1752);
        put(0,8,1131); put(1,8,2344); put(2,8,2856); put(3,8,1255); put(4,8,1504); put(5,8,1740); put(6,8,1899); put(7,8,1792);
        put(8,8,1099);
    }
}
