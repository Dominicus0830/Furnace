// N2b-0 정답기(V1 뼈대): 바닐라 jar 로 계산한 섹션 개수 3개·하이트맵(tools/vanilla-data/ExportSectionOracle)과 대조한다.
// 1) 상태별 개수 깃발이 Furnace 표(공기·무작위 틱·유체·용암)와 같은가.
// 2) 바닐라 개수 규칙(하나씩 놓기 / 다시 세기 — 유체 칸을 두 번 셈, 물도 tickingFluid 증가)의 본 모델이 정답과 같은가 — N2b-1 shim 이 이 규칙을 쓴다.
// 3) Furnace ChunkSection 의 비공기 수·무작위 틱 판정과 ChunkColumn 하이트맵이 같은 입력에서 바닐라와 같은가.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Fluids;
import org.junit.jupiter.api.Test;

class VanillaSectionOracleTest {

    private static final int F_NON_EMPTY = 1, F_TICK_BLOCK = 2, F_FLUID = 4, F_RECALC_FLUID_TICK = 8;

    private static int[] flags;
    private static final List<String[]> sections = new ArrayList<>();
    private static final List<String[]> columns = new ArrayList<>();

    private static synchronized void load() throws Exception {
        if (flags != null) return;
        try (InputStream in = VanillaSectionOracleTest.class.getResourceAsStream("/n2b/section-oracle-1.21.1.txt.gz")) {
            assertNotNull(in, "정답표 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            int[] f = null;
            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "version" -> assertEquals("1", p[1]);
                    case "states" -> f = new int[Integer.parseInt(p[1])];
                    case "flags" -> { for (int i = 0; i < f.length; i++) f[i] = Character.digit(p[1].charAt(i), 16); }
                    case "section" -> sections.add(p);
                    case "column" -> columns.add(p);
                    default -> fail("모르는 줄 " + p[0]);
                }
            }
            flags = f;
        }
    }

    @Test
    void stateFlagsMatchFurnaceTables() throws Exception {
        load();
        BlockBehaviors.ofBlock(0);
        Blocks bs = Blocks.get();
        assertEquals(flags.length, bs.stateCount(), "상태 수");
        List<String> bad = new ArrayList<>();
        for (int s = 0; s < flags.length; s++) {
            BlockBehavior b = BlockBehaviors.of(s);
            int fluid = b.getFluidState(s);
            int mine = (ChunkSection.isAir(s) ? 0 : F_NON_EMPTY)
                | (b.isRandomlyTicking(s) ? F_TICK_BLOCK : 0)
                | (Fluids.isEmpty(fluid) ? 0 : F_FLUID)
                | (Fluids.isLava(fluid) ? F_RECALC_FLUID_TICK : 0);
            if (mine != flags[s] && bad.size() < 20) bad.add(s + ":" + bs.describe(s) + " 바닐라=" + flags[s] + " Furnace=" + mine);
        }
        assertTrue(bad.isEmpty(), "상태 깃발 다름: " + bad);
    }

    /** 바닐라 LevelChunkSection 개수 규칙의 본 모델 — 하나씩 놓기. */
    private static void inc(int[] c, int old, int now) {
        int fo = flags[old], fn = flags[now];
        if ((fo & F_NON_EMPTY) != 0) { c[0]--; if ((fo & F_TICK_BLOCK) != 0) c[1]--; }
        if ((fo & F_FLUID) != 0) c[2]--;
        if ((fn & F_NON_EMPTY) != 0) { c[0]++; if ((fn & F_TICK_BLOCK) != 0) c[1]++; }
        if ((fn & F_FLUID) != 0) c[2]++;
    }

    /** 바닐라 recalcBlockCounts — 유체 칸은 nonEmpty 를 두 번 세고, tickingFluid 는 무작위 틱 유체(용암)만. */
    private static int[] recalc(int[] cells) {
        int[] c = new int[3];
        for (int s : cells) {
            int f = flags[s];
            if ((f & F_NON_EMPTY) != 0) { c[0]++; if ((f & F_TICK_BLOCK) != 0) c[1]++; }
            if ((f & F_FLUID) != 0) { c[0]++; if ((f & F_RECALC_FLUID_TICK) != 0) c[2]++; }
        }
        return c;
    }

    private static int[] ints(String[] p, int from) {
        return new int[]{Integer.parseInt(p[from]), Integer.parseInt(p[from + 1]), Integer.parseInt(p[from + 2])};
    }

    @Test
    void vanillaCountModelMatchesOracle() throws Exception {
        load();
        int[] pool = pool(flags);
        int quirkTicking = 0;
        for (String[] p : sections) {
            long seed = Long.parseLong(p[1]);
            SectionCase sc = SectionCase.make(seed, flags.length, pool);
            assertEquals(Long.parseLong(p[2]), sc.hash(), "생성기가 도구와 다름 seed=" + seed);
            int[] a = new int[3];
            for (int s : sc.cells) inc(a, 0, s);
            assertArrayEquals(ints(p, 3), a, "하나씩 놓기 seed=" + seed);
            int[] cells = sc.cells.clone();
            int[] b = recalc(cells);
            assertArrayEquals(ints(p, 6), b, "다시 세기 seed=" + seed);
            for (int k = 0; k < sc.opIdx.length; k++) {
                int i = sc.opIdx[k];
                inc(b, cells[i], sc.opState[k]);
                cells[i] = sc.opState[k];
            }
            // 바닐라는 개수를 short 로 둔다 — 범위 안이라 int 로 같다(음수도 그대로: 다시 센 뒤 물을 지우면 tickingFluid 가 0 아래로 간다).
            assertArrayEquals(ints(p, 9), b, "다시 센 뒤 하나씩 바꾸기 seed=" + seed);
            boolean vanillaTicks = b[1] > 0 || b[2] > 0;
            int[] fresh = recalc(cells);
            if (vanillaTicks != (fresh[1] > 0 || fresh[2] > 0)) quirkTicking++;
        }
        // 바닐라 특이점이 정답표에 실제로 들어 있는지(무작위 틱 판정이 내용과 어긋나는 경우 — R17, 별도 스레드가 다룬다)
        assertTrue(quirkTicking > 0, "정답표에 tickingFluid 특이점 사례가 없음");
        // 조사 문서(compat-nms-n2b0-survey 4장)에 인용한 정답표 통계를 못 박는다 — 생성기가 흥미로운 상태(물·용암·무작위 틱)를
        // 70% 고르는 합성 분포의 숫자이지 실제 월드 빈도가 아니다.
        int nonEmptyDiff = 0, tickingDiff = 0, negativeFluid = 0;
        for (String[] p : sections) {
            int[] a = ints(p, 3), b = ints(p, 6), c = ints(p, 9);
            if (a[0] != b[0]) nonEmptyDiff++;
            if ((a[1] > 0 || a[2] > 0) != (b[1] > 0 || b[2] > 0)) tickingDiff++;
            if (c[2] < 0) negativeFluid++;
        }
        assertEquals(289, nonEmptyDiff, "놓은 섹션과 읽은 섹션의 nonEmpty 가 다른 수");
        assertEquals(103, tickingDiff, "놓은 섹션과 읽은 섹션의 무작위 틱 판정이 다른 수");
        assertEquals(90, negativeFluid, "읽은 뒤 바꾼 섹션의 tickingFluid 음수 수");
    }

    /**
     * Furnace ChunkSection: 비공기 수는 바닐라 "하나씩 놓기" nonEmpty 와 같고(유체 두 번 세기 없음), 무작위 틱 판정은 바닐라
     * "하나씩 놓기" 와 같다(칸 쓰기는 물도 센다). 통째 교체 구역은 "다시 세기"와 같고, 그 뒤 하나씩 바꾼 결과도 정답표 세 번째 열과 같다(PR #358).
     */
    @Test
    void furnaceSectionMatchesVanillaOnSameInput() throws Exception {
        load();
        BlockBehaviors.ofBlock(0);
        int[] pool = pool(flags);
        for (String[] p : sections) {
            long seed = Long.parseLong(p[1]);
            SectionCase sc = SectionCase.make(seed, flags.length, pool);
            ChunkSection one = new ChunkSection();
            for (int i = 0; i < 4096; i++) one.setBlock(i & 15, i >> 8, (i >> 4) & 15, sc.cells[i]);
            int[] a = ints(p, 3), b = ints(p, 6);
            assertEquals(a[0], one.nonAirCount(), "비공기 수 seed=" + seed);
            assertEquals(a[1] > 0 || a[2] > 0, one.isRandomlyTicking(), "무작위 틱(하나씩 놓은 섹션) seed=" + seed);
            ChunkSection bulk = new ChunkSection();
            bulk.replaceBlocks(sc.cells.clone(), 0);
            assertEquals(a[0], bulk.nonAirCount(), "비공기 수(통째) seed=" + seed);
            assertEquals(b[1] > 0 || b[2] > 0, bulk.isRandomlyTicking(), "무작위 틱(통째) seed=" + seed);
            // 다시 센 뒤 하나씩 바꾸기(정답표 세 번째 열) — 센 값을 모르는(-1) 구역의 첫 쓰기는 먼저 다시 센 뒤 ±1 을 쌓아야 한다(PR #358)
            int[] c = ints(p, 9);
            for (int k = 0; k < sc.opIdx.length; k++) {
                int i = sc.opIdx[k];
                bulk.setBlock(i & 15, i >> 8, (i >> 4) & 15, sc.opState[k]);
            }
            assertEquals(c[1] > 0 || c[2] > 0, bulk.isRandomlyTicking(), "무작위 틱(통째 뒤 하나씩 바꾸기) seed=" + seed);
            assertArrayEquals(new int[] {c[1], c[2]}, bulk.countsForTest(), "센 값 seed=" + seed);
        }
    }

    @Test
    void furnaceColumnHeightmapsMatchVanilla() throws Exception {
        load();
        BlockBehaviors.ofBlock(0);
        int[] pool = pool(flags);
        for (int ci = 0; ci < columns.size(); ci += 2) {
            String[] prime = columns.get(ci), after = columns.get(ci + 1);
            long seed = Long.parseLong(prime[1]);
            assertEquals("prime", prime[3]);
            assertEquals("after", after[3]);
            ColumnCase cc = ColumnCase.make(seed, flags.length, pool);
            assertEquals(Long.parseLong(prime[2]), cc.hash(), "생성기가 도구와 다름 seed=" + seed);
            ChunkColumn col = new ChunkColumn(0);
            for (int y = 0; y < 384; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                int s = cc.cells[(y * 16 + z) * 16 + x];
                if (s != 0) col.setBlock(x, y - 64, z, s);
            }
            col.rebuildHeightmapsAfterSurface();   // 디스크에서 읽은 청크처럼 하이트맵을 내용에서 만든다(AnvilChunkCodec 과 같음)
            assertHeightmaps(prime, col, "처음 seed=" + seed, false);
            for (int k = 0; k < cc.opX.length; k++) col.setBlock(cc.opX[k], cc.opY[k] - 64, cc.opZ[k], cc.opState[k]);
            assertHeightmaps(after, col, "바꾼 뒤 seed=" + seed, true);
        }
        // 개수를 못 박는다: 빈 열 수정(PR #354)이 들어오면 0 이 되어 여기서 실패한다 — 그때 허용(allowEmptyColumn)을 지우고 엄격 비교로 바꿀 것.
        assertEquals(44, knownEmptyColumn, "빈 열 WORLD_SURFACE 알려진 차이 칸 수가 바뀜");
    }

    /**
     * 알려진 Furnace 차이(N2b-0 에서 발견, 별도 수정 과제): WORLD_SURFACE 가 0(열 전체가 공기)이 된 열은 ChunkColumn.updateSurface 가
     * "프라임 전"으로 보고 갱신을 건너뛰어, 그 뒤 놓은 블록이 반영되지 않는다(바닐라는 갱신). 그 모양(Furnace 0, 바닐라 0 아님)만 세고
     * 다른 차이는 모두 실패시킨다. 버그는 바꾼 뒤에만 생기므로 허용도 "바꾼 뒤" 단계에만 둔다(처음 = 내용에서 만든 값은 엄격).
     */
    private static int knownEmptyColumn;

    private static void assertHeightmaps(String[] p, ChunkColumn col, String what, boolean allowEmptyColumn) {
        long[][] mine = {col.worldSurfaceHeightmap(), col.oceanFloorHeightmap(), col.motionBlockingHeightmap(),
            col.motionBlockingNoLeavesHeightmap()};
        String[] names = {"WORLD_SURFACE", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"};
        for (int t = 0; t < 4; t++) {
            String[] hex = p[4 + t].split(",");
            long[] want = new long[hex.length];
            for (int i = 0; i < hex.length; i++) want[i] = Long.parseUnsignedLong(hex[i], 16);
            assertEquals(want.length, mine[t].length, names[t] + " 길이 " + what);
            for (int i = 0; i < 256; i++) {
                int v = (int) (want[i / 7] >>> ((i % 7) * 9)) & 511, m = (int) (mine[t][i / 7] >>> ((i % 7) * 9)) & 511;
                if (v == m) continue;
                if (allowEmptyColumn && t == 0 && m == 0 && v > 0) { knownEmptyColumn++; continue; }
                fail(names[t] + " " + what + " x=" + (i & 15) + " z=" + (i >> 4) + " 바닐라=" + v + " Furnace=" + m);
            }
        }
    }

    // ---- 아래 생성기는 tools/vanilla-data/ExportSectionOracle.java 와 글자 그대로 같아야 한다(해시로 확인) ----

    static int[] pool(int[] flags) {
        int c = 0;
        for (int f : flags) if (f != 1) c++;
        int[] p = new int[c];
        c = 0;
        for (int s = 0; s < flags.length; s++) if (flags[s] != 1) p[c++] = s;
        return p;
    }

    static int pick(SplittableRandom r, int n, int[] pool) {
        return r.nextInt(10) < 7 ? pool[r.nextInt(pool.length)] : r.nextInt(n);
    }

    static final class SectionCase {
        int[] cells = new int[4096];
        int[] opIdx, opState;

        static SectionCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed);
            SectionCase c = new SectionCase();
            int k = 1 + r.nextInt(24);
            int[] pal = new int[k];
            for (int i = 0; i < k; i++) pal[i] = pick(r, n, pool);
            int airPct = r.nextInt(101);
            for (int i = 0; i < 4096; i++) c.cells[i] = r.nextInt(100) < airPct ? 0 : pal[r.nextInt(k)];
            int m = r.nextInt(400);
            c.opIdx = new int[m];
            c.opState = new int[m];
            for (int i = 0; i < m; i++) {
                c.opIdx[i] = r.nextInt(4096);
                int q = r.nextInt(3);
                c.opState[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int v : opIdx) h = 31 * h + v;
            for (int v : opState) h = 31 * h + v;
            return h;
        }
    }

    static final class ColumnCase {
        int[] cells = new int[384 * 256]; // (y*16+z)*16+x, y 는 0..383 (= 블록 y + 64)
        int[] opX, opY, opZ, opState;

        static ColumnCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed);
            ColumnCase c = new ColumnCase();
            int k = 2 + r.nextInt(16);
            int[] pal = new int[k];
            for (int i = 0; i < k; i++) pal[i] = pick(r, n, pool);
            int base = r.nextInt(384);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int top = Math.max(0, Math.min(383, base + r.nextInt(33) - 16));
                for (int y = 0; y <= top; y++) {
                    int q = r.nextInt(10);
                    c.cells[(y * 16 + z) * 16 + x] = q < 2 ? 0 : pal[r.nextInt(k)];
                }
            }
            int m = r.nextInt(2000);
            c.opX = new int[m]; c.opY = new int[m]; c.opZ = new int[m]; c.opState = new int[m];
            for (int i = 0; i < m; i++) {
                c.opX[i] = r.nextInt(16);
                c.opZ[i] = r.nextInt(16);
                c.opY[i] = Math.max(0, Math.min(383, base + r.nextInt(49) - 24));
                int q = r.nextInt(3);
                c.opState[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int i = 0; i < opX.length; i++) h = 31 * (31 * (31 * (31 * h + opX[i]) + opY[i]) + opZ[i]) + opState[i];
            return h;
        }
    }
}
