// F-05: 섹터 할당기 데이터 손상 방지 시험. 바닐라 1.21.1 RegionFile/RegionBitmap 알고리즘을 시험 안에 복제한 참조 모델과
// 오프셋·섹터 사용을 대조하고, 임의 지점 크래시·옛 Furnace 파일·레인 밖 동시 쓰기·겹친 헤더를 검사한다.
package org.domi.furnace.server.world.anvil;

import static org.junit.jupiter.api.Assertions.*;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RegionSectorAllocatorTest {

    static final int SECTOR = 4096;

    /** 바닐라 참조 모델: RegionBitmap(첫 적합) + RegionFile 생성자 스캔 + write(새 자리 할당 → 헤더 → 옛 해제). */
    static final class VanillaRef {
        final BitSet bits = new BitSet();
        final int[] loc = new int[1024];

        VanillaRef() { bits.set(0, 2); }

        /** 생성자 스캔(바닐라와 같은 검사). */
        static VanillaRef scan(int[] header, long fileSize) {
            VanillaRef r = new VanillaRef();
            for (int i = 0; i < 1024; i++) {
                int l = header[i];
                if (l == 0) continue;
                int off = (l >> 8) & 0xFFFFFF, cnt = l & 0xFF;
                if (off < 2 || cnt == 0 || off * 4096L > fileSize) continue;
                r.bits.set(off, off + cnt);
                r.loc[i] = l;
            }
            return r;
        }

        int allocate(int n) {
            int from = 0;
            while (true) {
                int c = bits.nextClearBit(from);
                int s = bits.nextSetBit(c);
                if (s == -1 || s - c >= n) { bits.set(c, c + n); return c; }
                from = s;
            }
        }

        /** remaining = 5 + 압축 길이. 바닐라: sectors >= 256 이면 외부(1섹터 스텁). */
        void write(int idx, int remaining) {
            int sectors = (remaining + 4095) / 4096;
            if (sectors >= 256) sectors = 1;
            int old = loc[idx];
            int off = allocate(sectors);
            loc[idx] = off << 8 | sectors;
            if (old != 0) bits.clear(old >> 8 & 0xFFFFFF, (old >> 8 & 0xFFFFFF) + (old & 0xFF));
        }
    }

    /** 섹터 수가 다양한 압축 본문 풀. 각 원본은 내용이 달라 읽기로 어느 것인지 확인된다. */
    static final class Pool {
        final byte[][] raw, comp;

        Pool(Random r, boolean withOversized) throws java.io.IOException {
            List<byte[]> raws = new ArrayList<>();
            int[] sectorTargets = {1, 1, 1, 2, 2, 3, 4, 5, 6, 8, 10, 13, 17, 24, 33, 50, 80, 120, 200, 254};
            for (int t = 0; t < sectorTargets.length; t++) {
                for (int v = 0; v < 2; v++) {
                    int len = Math.max(16, sectorTargets[t] * SECTOR - 400 - r.nextInt(2000));
                    byte[] b = new byte[len];
                    r.nextBytes(b);   // 거의 압축 안 됨 → 섹터 수가 목표 근처
                    b[0] = (byte) raws.size();
                    raws.add(b);
                }
            }
            if (withOversized) {
                byte[] big = new byte[1_100_000];
                r.nextBytes(big);
                raws.add(big);
            }
            raw = raws.toArray(new byte[0][]);
            comp = new byte[raw.length][];
            for (int i = 0; i < raw.length; i++) comp[i] = Compression.deflate(raw[i]);
        }

        int pick(Random r) {
            if (raw.length % 2 == 1 && r.nextInt(400) == 0) return raw.length - 1;   // 외부 청크는 드물게
            int n = raw.length - (raw.length % 2);
            // 작은 청크 위주(실제 청크 분포 비슷), 가끔 큰 것
            return r.nextInt(4) == 0 ? r.nextInt(n) : r.nextInt(Math.min(n, 16));
        }
    }

    static int[] readHeader(Path f) throws Exception {
        int[] h = new int[1024];
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "r")) {
            for (int i = 0; i < 1024; i++) h[i] = raf.readInt();
        }
        return h;
    }

    static void assertSame(VanillaRef ref, RegionFile rf, String ctx) {
        for (int i = 0; i < 1024; i++) {
            if (ref.loc[i] != rf.location(i)) fail(ctx + ": 항목 " + i + " 참조=" + Integer.toHexString(ref.loc[i]) + " 실제=" + Integer.toHexString(rf.location(i)));
        }
        assertEquals(ref.bits, rf.usedSectors(), ctx + ": 섹터 비트맵");
    }

    static void assertContents(RegionFile rf, Pool pool, int[] expect, String ctx) throws Exception {
        for (int i = 0; i < 1024; i++) {
            byte[] got = rf.readChunk(i & 31, i >> 5);
            if (expect[i] < 0) assertNull(got, ctx + ": 청크 " + i);
            else assertArrayEquals(pool.raw[expect[i]], got, ctx + ": 청크 " + i);
        }
    }

    /** 살아 있는 항목끼리 섹터가 겹치지 않는다. */
    static void assertNoOverlap(RegionFile rf) {
        BitSet seen = new BitSet();
        seen.set(0, 2);
        for (int i = 0; i < 1024; i++) {
            int l = rf.location(i);
            if (l == 0) continue;
            int off = l >>> 8, cnt = l & 0xFF;
            int n = seen.nextSetBit(off);
            assertFalse(n != -1 && n < off + cnt, "항목 " + i + " 겹침");
            seen.set(off, off + cnt);
        }
        assertEquals(seen, rf.usedSectors(), "비트맵 = 헤더 + 살아 있는 항목");
    }

    // (1) 속성 시험: 무작위 쓰기·덮어쓰기·크기 변화를 바닐라 참조 모델과 매 연산 대조, 주기적으로 다시 열기
    @Test
    void randomSequences_matchVanillaReference(@TempDir Path dir) throws Exception {
        int seeds = 4, ops = 15_000;
        for (int seed = 0; seed < seeds; seed++) {
            Random r = new Random(0xF05_0000L + seed);
            Pool pool = new Pool(r, true);
            Path f = dir.resolve("s" + seed).resolve("r.0.0.mca");
            int[] expect = new int[1024];
            Arrays.fill(expect, -1);
            VanillaRef ref = new VanillaRef();
            // 시드마다 쓰는 칸 수를 달리해 덮어쓰기 비율을 바꾼다
            int slots = new int[]{8, 64, 300, 1024}[seed];
            RegionFile rf = new RegionFile(f);
            for (int n = 0; n < ops; n++) {
                int idx = r.nextInt(slots);
                int p = pool.pick(r);
                rf.writeCompressed(idx & 31, idx >> 5, pool.comp[p]);
                ref.write(idx, pool.comp[p].length + 5);
                expect[idx] = p;
                assertSame(ref, rf, "seed " + seed + " op " + n);
                if (n % 2500 == 2499) {
                    rf.close();
                    ref = VanillaRef.scan(readHeader(f), Files.size(f));
                    rf = new RegionFile(f);
                    assertSame(ref, rf, "reopen seed " + seed + " op " + n);
                    assertContents(rf, pool, expect, "seed " + seed + " op " + n);
                }
            }
            assertNoOverlap(rf);
            rf.close();
            assertEquals(0, Files.size(f) % SECTOR, "파일 길이 섹터 배수");
            int maxEnd = ref.bits.length();
            assertTrue(Files.size(f) / SECTOR >= maxEnd, "사용 섹터가 파일 안");
        }
    }

    // 누수 없음: 같은 칸을 같은 크기로 수천 번 덮어써도 파일이 커지지 않는다(F-05 의 원래 증상)
    @Test
    void repeatedRewrite_doesNotGrow(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        Random r = new Random(5);
        Pool pool = new Pool(r, false);
        try (RegionFile rf = new RegionFile(f)) {
            for (int n = 0; n < 5000; n++) {
                int idx = r.nextInt(16);
                rf.writeCompressed(idx & 31, idx >> 5, pool.comp[r.nextInt(16)]);
            }
            // 16칸 × 최대 8섹터 × 2(옛+새) + 헤더 2 를 넘지 않는다
            assertTrue(rf.usedSectors().length() <= 2 + 16 * 8 * 2, "사용 섹터 " + rf.usedSectors().length());
        }
        assertTrue(Files.size(f) <= (2 + 16 * 8 * 2 + 8) * (long) SECTOR, "파일 " + Files.size(f));
    }

    // (2) 크래시 모의: 임의 바이트 지점에서 중단 → 다시 열면 모든 청크가 옛 값 또는 (대상 청크만) 새 값. 이어서 쓰기도 안전.
    @Test
    void crashAtArbitraryPoint_everyChunkOldOrNew(@TempDir Path dir) throws Exception {
        Random r = new Random(0xC4A5);
        Pool pool = new Pool(r, true);
        int trials = 600;
        for (int t = 0; t < trials; t++) {
            Path d = dir.resolve("t" + t);
            Path f = d.resolve("r.0.0.mca");
            int[] expect = new int[1024];
            Arrays.fill(expect, -1);
            try (RegionFile rf = new RegionFile(f)) {
                int pre = 20 + r.nextInt(120);
                for (int n = 0; n < pre; n++) {
                    int idx = r.nextInt(64);
                    int p = r.nextInt(30) == 0 ? pool.raw.length - 1 : pool.pick(r);
                    rf.writeCompressed(idx & 31, idx >> 5, pool.comp[p]);
                    expect[idx] = p;
                }
            }
            int target = r.nextInt(64);
            int p = r.nextInt(8) == 0 ? pool.raw.length - 1 : pool.pick(r);
            long opBytes = pool.comp[p].length + 8192L + SECTOR + 4;
            RegionFile rf = new RegionFile(f);
            rf.crashBudget = (long) (r.nextDouble() * opBytes);
            boolean crashed = false;
            try {
                rf.writeCompressed(target & 31, target >> 5, pool.comp[p]);
            } catch (RegionFile.SimulatedCrash e) {
                crashed = true;
            }
            rf.close();   // 프로세스 크래시: 메모리 상태는 버리고 디스크만 남는다
            try (RegionFile re = new RegionFile(f)) {
                for (int i = 0; i < 1024; i++) {
                    byte[] got = re.readChunk(i & 31, i >> 5);
                    if (i == target) {
                        boolean isNew = Arrays.equals(got, pool.raw[p]);
                        boolean isOld = expect[i] < 0 ? got == null : Arrays.equals(got, pool.raw[expect[i]]);
                        assertTrue(isNew || isOld, "trial " + t + " 대상 청크가 옛 값도 새 값도 아님(crashed=" + crashed + ")");
                        if (!crashed) assertTrue(isNew, "trial " + t + " 완료했는데 새 값 아님");
                        expect[i] = isNew ? p : expect[i];
                    } else if (expect[i] < 0) {
                        assertNull(got, "trial " + t + " 청크 " + i);
                    } else {
                        assertArrayEquals(pool.raw[expect[i]], got, "trial " + t + " 다른 청크 " + i + " 손상");
                    }
                }
                // 크래시 뒤 이어 쓰기: 새 비트맵이 살아 있는 데이터를 덮지 않는다
                for (int n = 0; n < 30; n++) {
                    int idx = r.nextInt(64);
                    int q = pool.pick(r);
                    re.writeCompressed(idx & 31, idx >> 5, pool.comp[q]);
                    expect[idx] = q;
                }
                assertNoOverlap(re);
                assertContents(re, pool, expect, "trial " + t + " after");
            }
        }
    }

    /** 옛 Furnace 할당기(in-place 가능하면 덮어쓰기, 아니면 최대 끝 뒤에 붙임 — 옛 섹터 누수)를 그대로 복제해 파일을 만든다. */
    static void legacyWrite(RandomAccessFile raf, int[] loc, int idx, byte[] comp) throws Exception {
        int needed = (comp.length + 5 + SECTOR - 1) / SECTOR;
        int old = loc[idx], offset;
        if (old != 0 && needed <= (old & 0xFF)) offset = old >>> 8;
        else {
            int max = 2;
            for (int l : loc) max = Math.max(max, (l >>> 8) + (l & 0xFF));
            raf.setLength((long) (max + needed) * SECTOR);
            offset = max;
        }
        raf.seek((long) offset * SECTOR);
        raf.writeInt(comp.length + 1);
        raf.writeByte(2);
        raf.write(comp);
        loc[idx] = offset << 8 | needed;
        raf.seek(idx * 4L);
        raf.writeInt(loc[idx]);
    }

    // (3) 호환: 옛 Furnace 가 만든 낭비 섹터가 있는 .mca 를 열어 계속 쓴다 — 구멍을 바닐라처럼 재사용하고 기존 청크는 그대로
    @Test
    void legacyFurnaceFile_reusesLeakedSectors(@TempDir Path dir) throws Exception {
        Random r = new Random(0x1E6);
        Pool pool = new Pool(r, false);
        Path f = dir.resolve("r.0.0.mca");
        Files.createDirectories(dir);
        int[] loc = new int[1024];
        int[] expect = new int[1024];
        Arrays.fill(expect, -1);
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "rw")) {
            raf.setLength(2L * SECTOR);
            for (int n = 0; n < 3000; n++) {
                int idx = r.nextInt(200);
                int p = pool.pick(r);
                legacyWrite(raf, loc, idx, pool.comp[p]);
                expect[idx] = p;
            }
        }
        long legacySize = Files.size(f);
        VanillaRef ref = VanillaRef.scan(readHeader(f), legacySize);
        int liveSectors = ref.bits.cardinality();
        assertTrue(liveSectors < legacySize / SECTOR / 2, "시험 전제: 옛 파일에 낭비 섹터가 많다");
        try (RegionFile rf = new RegionFile(f)) {
            assertSame(ref, rf, "open legacy");
            assertContents(rf, pool, expect, "legacy");
            for (int n = 0; n < 5000; n++) {
                int idx = r.nextInt(200);
                int p = pool.pick(r);
                rf.writeCompressed(idx & 31, idx >> 5, pool.comp[p]);
                ref.write(idx, pool.comp[p].length + 5);
                expect[idx] = p;
                assertSame(ref, rf, "legacy op " + n);
            }
            assertNoOverlap(rf);
            assertContents(rf, pool, expect, "legacy after");
        }
        assertEquals(legacySize, Files.size(f), "구멍만 재사용하고 파일을 늘리지 않았다");
    }

    // (4) 레인 밖 동시 쓰기: 한 RegionFile 에 여러 스레드가 직접 쓴다 — 비트맵·헤더 일관성과 내용 유지
    @Test
    void concurrentWritersOutsideLanes_keepBitmapConsistent(@TempDir Path dir) throws Exception {
        Random seedR = new Random(0xC0C);
        Pool pool = new Pool(seedR, false);
        Path f = dir.resolve("r.0.0.mca");
        int threads = 8, perThread = 1500;
        int[] expect = new int[1024];
        Arrays.fill(expect, -1);
        try (RegionFile rf = new RegionFile(f)) {
            List<Thread> ts = new ArrayList<>();
            int[][] lastByThread = new int[threads][1024];
            Throwable[] err = new Throwable[1];
            for (int t = 0; t < threads; t++) {
                final int tt = t;
                Arrays.fill(lastByThread[t], -1);
                Thread th = new Thread(() -> {
                    Random r = new Random(tt);
                    try {
                        for (int n = 0; n < perThread; n++) {
                            int idx = tt * 128 + r.nextInt(128);   // 스레드마다 다른 청크(마지막 값이 결정적)
                            int p = pool.pick(r);
                            rf.writeCompressed(idx & 31, idx >> 5, pool.comp[p]);
                            lastByThread[tt][idx] = p;
                            if (n % 7 == 0) rf.readChunk(r.nextInt(32), r.nextInt(32));
                        }
                    } catch (Throwable e) {
                        synchronized (err) { err[0] = e; }
                    }
                });
                ts.add(th);
                th.start();
            }
            for (Thread th : ts) th.join();
            assertNull(err[0], () -> "스레드 오류: " + err[0]);
            for (int t = 0; t < threads; t++)
                for (int i = 0; i < 1024; i++) if (lastByThread[t][i] >= 0) expect[i] = lastByThread[t][i];
            assertNoOverlap(rf);
            assertContents(rf, pool, expect, "concurrent");
        }
        try (RegionFile rf = new RegionFile(f)) {
            assertNoOverlap(rf);
            assertContents(rf, pool, expect, "concurrent reopen");
        }
    }

    // (5) 겹친 헤더 방어: 두 항목이 같은 섹터를 가리키는 파일에서 한쪽을 다시 써도 다른 쪽이 덮이지 않는다
    @Test
    void overlappingHeaderEntries_survivorNotClobbered(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        byte[] a = "chunk-a".getBytes(), b = "chunk-b".getBytes();
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(0, 0, a);   // 섹터 2
            rf.writeChunk(1, 0, b);   // 섹터 3
        }
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "rw")) {
            raf.seek(4 * 5);
            raf.writeInt(2 << 8 | 2);   // 항목 5 가 섹터 2..3 을 가리킨다(0·1 과 겹침)
        }
        try (RegionFile rf = new RegionFile(f)) {
            rf.writeChunk(5, 0, "x".getBytes());   // 항목 5 해제 → 2..3 이 비면 안 된다
            rf.writeChunk(6, 0, "y".getBytes());
            rf.writeChunk(7, 0, "z".getBytes());
            assertArrayEquals(a, rf.readChunk(0, 0));
            assertArrayEquals(b, rf.readChunk(1, 0));
            assertArrayEquals("x".getBytes(), rf.readChunk(5, 0));
            assertNoOverlap(rf);
        }
    }

    // 바닐라 생성자 검사: 헤더와 겹치는·0섹터·파일 밖 항목은 지우고 비트맵에 넣지 않는다(그 자리를 재사용해도 안전)
    @Test
    void invalidEntries_droppedLikeVanilla(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.0.0.mca");
        try (RegionFile rf = new RegionFile(f)) { rf.writeChunk(0, 0, "a".getBytes()); }
        try (RandomAccessFile raf = new RandomAccessFile(f.toFile(), "rw")) {
            raf.seek(4); raf.writeInt(1 << 8 | 1);          // 헤더와 겹침
            raf.seek(8); raf.writeInt(3 << 8);              // 0섹터
            raf.seek(12); raf.writeInt(100_000 << 8 | 1);   // 파일 밖
        }
        VanillaRef ref = VanillaRef.scan(readHeader(f), Files.size(f));
        try (RegionFile rf = new RegionFile(f)) {
            assertSame(ref, rf, "invalid");
            assertFalse(rf.hasChunk(1, 0));
            assertArrayEquals("a".getBytes(), rf.readChunk(0, 0));
        }
    }
}
