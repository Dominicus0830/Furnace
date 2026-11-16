// N2b-2 정답기(V1): 읽은 청크에 NMS 원시 쓰기(섹션 setBlockState·컨테이너 set·Heightmap.update/setRawData·개수 대입)와 보통 블록 쓰기를 섞었을 때
// Furnace ChunkColumn 의 하이트맵 넷(고정 하이트맵 모드)과 섹션의 바닐라 개수 셋이 바닐라 jar 결과(tools/vanilla-data/ExportRawWriteOracle)와 같은가.
package org.domi.furnace.server.world;

import org.domi.furnace.api.GameVersion;
import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.zip.GZIPInputStream;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Fluids;
import org.junit.jupiter.api.Test;

class VanillaRawWriteOracleTest {

    private static List<String[]> cases;

    private static synchronized List<String[]> load() throws Exception {
        if (cases != null) return cases;
        List<String[]> out = new ArrayList<>();
        try (InputStream in = VanillaRawWriteOracleTest.class.getResourceAsStream("/n2b/" + GameVersion.RESOURCE_TAG + "/rawwrite-oracle.txt.gz")) {
            assertNotNull(in, "정답표 없음");
            BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8));
            for (String line; (line = r.readLine()) != null; ) {
                String[] p = line.split(" ");
                switch (p[0]) {
                    case "version" -> assertEquals("1", p[1]);
                    case "raw" -> out.add(p);
                    default -> fail("모르는 줄 " + p[0]);
                }
            }
        }
        return cases = out;
    }

    /** 도구와 같은 깃발(1 = 공기 아님, 2 = 무작위 틱 블록, 4 = 유체 있음) — VanillaSectionOracleTest 가 바닐라와 같음을 확인한다. */
    private static int[] flags() {
        BlockBehaviors.ofBlock(0);
        int n = Blocks.get().stateCount();
        int[] f = new int[n];
        for (int s = 0; s < n; s++) {
            BlockBehavior b = BlockBehaviors.of(s);
            f[s] = (ChunkSection.isAir(s) ? 0 : 1) | (b.isRandomlyTicking(s) ? 2 : 0) | (Fluids.isEmpty(b.getFluidState(s)) ? 0 : 4);
        }
        return f;
    }

    private static long[] hex(String s) {
        String[] h = s.split(",");
        long[] v = new long[h.length];
        for (int i = 0; i < h.length; i++) v[i] = Long.parseUnsignedLong(h[i], 16);
        return v;
    }

    @Test
    void rawWritesMatchVanillaHeightmapsAndCounts() throws Exception {
        int[] flags = flags();
        int[] pool = pool(flags);
        HeightmapType[] types = HeightmapType.values();
        String[] names = {"WORLD_SURFACE", "OCEAN_FLOOR", "MOTION_BLOCKING", "MOTION_BLOCKING_NO_LEAVES"};
        int staleCells = 0, gatedSections = 0;
        for (String[] p : load()) {
            long seed = Long.parseLong(p[1]);
            RawCase rc = RawCase.make(seed, flags.length, pool);
            assertEquals(Long.parseLong(p[2]), rc.hash(), "생성기가 도구와 다름 seed=" + seed);
            ChunkColumn col = new ChunkColumn(0);
            for (int si = 0; si < 24; si++) col.loadSectionBlocks(si, Arrays.copyOfRange(rc.cells, si * 4096, si * 4096 + 4096));
            col.rebuildHeightmapsAfterSurface();   // 디스크에서 읽은 청크(AnvilChunkCodec 과 같은 순서)
            for (int k = 0; k < rc.kind.length; k++) {
                int x = rc.x[k], y = rc.y[k], z = rc.z[k], st = rc.state[k], wy = y - 64, si = y >> 4;
                int cell = (y & 15) * 256 + z * 16 + x;
                switch (rc.kind[k]) {
                    case 0 -> {   // ChunkLevel.setBlockStateInChunk 의 블록·하이트맵 부분
                        int old = col.getBlock(x, wy, z);
                        if (old == st) break;
                        if (ChunkSection.isAir(st) && col.sectionVanillaHasOnlyAir(wy)) break;
                        col.setBlock(x, wy, z, st);
                    }
                    case 1 -> col.setSectionStateRaw(si, cell, st, true);
                    case 2 -> col.setSectionStateRaw(si, cell, st, false);
                    case 3 -> col.heightmapUpdate(types[rc.type[k]], x, wy, z, st);
                    case 4, 5 -> assertEquals(rc.kind[k] == 4, col.setHeightmapRaw(types[rc.type[k]], rc.raw[k].clone()));
                    case 6 -> col.setSectionVanillaCounts(si, rc.counts[k][0], rc.counts[k][1], rc.counts[k][2]);
                    default -> fail();
                }
            }
            long[][] mine = {col.worldSurfaceHeightmap(), col.oceanFloorHeightmap(), col.motionBlockingHeightmap(), col.motionBlockingNoLeavesHeightmap()};
            for (int t = 0; t < 4; t++) {
                long[] want = hex(p[3 + t]);
                if (!Arrays.equals(want, mine[t])) {
                    for (int i = 0; i < 256; i++) {
                        int v = (int) (want[i / 7] >>> ((i % 7) * 9)) & 511, m = (int) (mine[t][i / 7] >>> ((i % 7) * 9)) & 511;
                        if (v != m) fail(names[t] + " seed=" + seed + " x=" + (i & 15) + " z=" + (i >> 4) + " 바닐라=" + v + " Furnace=" + m);
                    }
                    fail(names[t] + " seed=" + seed + " raw 다름");
                }
            }
            String[] cnt = p[7].split(",");
            assertEquals(24, cnt.length);
            for (int si = 0; si < 24; si++) {
                String[] c = cnt[si].split("/");
                int[] want = {Integer.parseInt(c[0]), Integer.parseInt(c[1]), Integer.parseInt(c[2])};
                assertArrayEquals(want, col.sectionVanillaCounts(si), "개수 seed=" + seed + " 섹션 " + si);
                // 바닐라 무작위 틱 대상 판정과 T-053 마스크도 그 개수를 따른다
                assertEquals(want[1] > 0 || want[2] > 0, col.sectionForRead(si).isRandomlyTicking(), "무작위 틱 seed=" + seed + " 섹션 " + si);
                if (want[0] == 0 && !col.sectionForRead(si).hasOnlyAir()) gatedSections++;
            }
            assertTrue(col.randomTickMaskCovers(), "마스크 seed=" + seed);
            // 고정 하이트맵이 실제로 낡은 값을 들고 있는지(지금 블록에서 다시 센 값과 다른 칸 수)
            if (col.heightmapsFixed()) {
                ChunkColumn fresh = new ChunkColumn(0);
                for (int si = 0; si < 24; si++) fresh.loadSectionBlocks(si, col.sectionForRead(si).blocks());
                fresh.rebuildHeightmapsAfterSurface();
                long[] a = fresh.worldSurfaceHeightmap(), b = col.worldSurfaceHeightmap();
                for (int i = 0; i < 256; i++) if (((a[i / 7] ^ b[i / 7]) >>> ((i % 7) * 9) & 511) != 0) staleCells++;
            }
        }
        assertEquals(60, load().size());
        assertTrue(staleCells > 1000, "원시 쓰기 뒤 낡은 WORLD_SURFACE 칸이 충분히 나와야 시험이 뜻이 있다: " + staleCells);
        assertTrue(gatedSections > 0, "개수 0 인데 블록이 있는 섹션(바닐라 hasOnlyAir 가 칸과 어긋남) 사례가 있어야 한다");
    }

    // ---- 아래 생성기는 tools/vanilla-data/ExportRawWriteOracle.java 와 글자 그대로 같아야 한다(해시로 확인) ----

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

    /** 하이트맵 raw(값 256개, 9비트, long 당 7개 — 384 높이 바닐라 배치). 값은 0..511(높이 밖 값도 바닐라는 그대로 받는다). */
    static long[] packHeights(SplittableRandom r, int base) {
        long[] raw = new long[37];
        for (int i = 0; i < 256; i++) {
            int q = r.nextInt(4);
            long v = q == 0 ? r.nextInt(512) : q == 1 ? 0 : Math.max(0, Math.min(384, base + 1 + r.nextInt(17) - 8));
            raw[i / 7] |= v << ((i % 7) * 9);
        }
        return raw;
    }

    static final class RawCase {
        int[] cells = new int[384 * 256]; // (y*16+z)*16+x, y 는 0..383 (= 블록 y + 64)
        int[] kind, x, y, z, state, type;
        long[][] raw;
        int[][] counts;

        static RawCase make(long seed, int n, int[] pool) {
            SplittableRandom r = new SplittableRandom(seed * 7919 + 17);
            RawCase c = new RawCase();
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
            int m = r.nextInt(1500);
            c.kind = new int[m]; c.x = new int[m]; c.y = new int[m]; c.z = new int[m]; c.state = new int[m]; c.type = new int[m];
            c.raw = new long[m][]; c.counts = new int[m][];
            for (int i = 0; i < m; i++) {
                int p = r.nextInt(100);
                c.kind[i] = p < 40 ? 0 : p < 62 ? 1 : p < 82 ? 2 : p < 91 ? 3 : p < 95 ? 4 : p < 97 ? 5 : 6;
                c.x[i] = r.nextInt(16);
                c.z[i] = r.nextInt(16);
                c.y[i] = Math.max(0, Math.min(383, base + r.nextInt(49) - 24));
                int q = r.nextInt(3);
                c.state[i] = q == 0 ? 0 : q == 1 ? pal[r.nextInt(k)] : pick(r, n, pool);
                c.type[i] = r.nextInt(4);
                if (c.kind[i] == 4) c.raw[i] = packHeights(r, base);
                if (c.kind[i] == 5) {
                    long[] bad = new long[r.nextBoolean() ? 36 : 38];
                    for (int j = 0; j < bad.length; j++) bad[j] = r.nextLong();
                    c.raw[i] = bad;
                }
                if (c.kind[i] == 6) {
                    c.counts[i] = new int[3];
                    for (int j = 0; j < 3; j++) c.counts[i][j] = r.nextInt(3) == 0 ? 0 : r.nextInt(41) - 20;
                }
            }
            return c;
        }

        long hash() {
            long h = 1125899906842597L;
            for (int v : cells) h = 31 * h + v;
            for (int i = 0; i < kind.length; i++) {
                h = 31 * (31 * (31 * (31 * (31 * (31 * h + kind[i]) + x[i]) + y[i]) + z[i]) + state[i]) + type[i];
                if (raw[i] != null) for (long v : raw[i]) h = 31 * h + v;
                if (counts[i] != null) for (int v : counts[i]) h = 31 * h + v;
            }
            return h;
        }
    }
}
