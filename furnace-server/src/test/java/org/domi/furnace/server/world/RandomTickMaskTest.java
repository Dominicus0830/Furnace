// T-053: 컬럼의 무작위 틱 구역 마스크가 모든 쓰기 경로(컬럼 setBlock·생성 블록·구역 직접 쓰기·디스크 적재 두 꼴·scratch+compact·
// 생성 결과 통째 넣기·copy·cowCopy·빛 작업 얼림/녹임)를 섞어도 "무작위 틱 받는 구역을 놓치지 않는다". 매 연산 뒤 마스크로 훑은 구역 집합이
// 4096칸 전수(브루트)로 센 집합과 같아야 하고, randomTickMaskCovers(-ea 단언과 같은 검사)가 참이어야 한다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.SplittableRandom;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.identity.Differential;
import org.junit.jupiter.api.Test;

class RandomTickMaskTest {

    private static int[] ticking, plain;
    private static boolean[] isTicking;

    private static void init() {
        if (ticking != null) return;
        BlockBehaviors.ofBlock(0);   // 무작위 틱 표를 섹션에 건다
        Blocks bs = Blocks.get();
        List<Integer> t = new ArrayList<>(), p = new ArrayList<>();
        for (int s = 0; s < bs.stateCount(); s += 7) {
            if (BlockBehaviors.of(s).isRandomlyTicking(s)) t.add(s);
            else p.add(s);
        }
        isTicking = new boolean[bs.stateCount()];
        for (int s = 0; s < bs.stateCount(); s++) isTicking[s] = BlockBehaviors.of(s).isRandomlyTicking(s);
        ticking = t.stream().mapToInt(Integer::intValue).toArray();
        plain = p.stream().mapToInt(Integer::intValue).toArray();
        assertTrue(ticking.length > 10 && plain.length > 10);
    }

    /** 연산 한 줄: 종류와 난수에서 뽑은 인자. */
    private record Op(int kind, long arg) {}

    /** 입력: 컬럼 모양(키 큰 차원이면 구역 70개 — 64 넘는 구역은 비트 없이 늘 확인) + 연산 열. */
    private record Input(boolean tall, List<Op> ops) {
        String summary() { return (tall ? "tall " : "") + ops.size() + " ops kinds=" + ops.stream().map(o -> String.valueOf(o.kind)).reduce("", String::concat); }
    }

    private static Input input(SplittableRandom r) {
        init();
        boolean tall = r.nextInt(6) == 0;
        List<Op> ops = new ArrayList<>();
        int n = 20 + r.nextInt(60);
        for (int i = 0; i < n; i++) {
            int k = r.nextInt(100);
            int kind = r.nextInt(100) < 15 ? (r.nextBoolean() ? 10 : 11) : k < 30 ? 0 : k < 40 ? 1 : k < 55 ? 2 : k < 62 ? 3 : k < 68 ? 4 : k < 74 ? 5 : k < 78 ? 6 : k < 84 ? 7 : k < 97 ? 8 : 9;
            ops.add(new Op(kind, r.nextLong()));
        }
        return new Input(tall, ops);
    }

    private static int state(SplittableRandom r, boolean tickingBias) {
        if (r.nextInt(tickingBias ? 3 : 8) == 0) return ticking[r.nextInt(ticking.length)];
        return r.nextInt(4) == 0 ? ChunkSection.AIR : plain[r.nextInt(plain.length)];
    }

    /** 구역 한 칸 값 4096개 — 대부분 몇 가지 값(간접 팔레트), 가끔 무작위 틱 상태 몇 칸 또는 하나도 없음. */
    private static int[] flat(SplittableRandom r) {
        int[] vals = new int[1 + r.nextInt(5)];
        for (int i = 0; i < vals.length; i++) vals[i] = state(r, false);
        int[] f = new int[4096];
        for (int i = 0; i < 4096; i++) f[i] = vals[r.nextInt(vals.length)];
        int sprinkle = r.nextInt(3) == 0 ? 0 : r.nextInt(4);
        for (int i = 0; i < sprinkle; i++) f[r.nextInt(4096)] = ticking[r.nextInt(ticking.length)];
        return f;
    }

    /** 빛 작업(T-064)이 쥐고 있는 얼린 구역 묶음 — 그 사이 컬럼 쓰기는 replaceSection 복사본으로 간다(PR #309 검토 R1). 시험 스레드 하나만 쓴다. */
    private static final List<ChunkColumn.LightBlocks> FROZEN = new ArrayList<>();

    private static void thawAll() {
        for (ChunkColumn.LightBlocks lb : FROZEN) lb.thaw();
        FROZEN.clear();
    }

    private static ChunkColumn fresh(boolean tall) {
        thawAll();
        return tall ? new ChunkColumn(0, 15, 7, -64, 70) : new ChunkColumn(0, 15, 7, ChunkColumn.MIN_Y, ChunkColumn.SECTION_COUNT);
    }

    /** 연산 하나를 적용하고 이어서 쓸 컬럼을 돌려준다(copy·cowCopy 는 사본으로 바꿔 탄다). */
    private static ChunkColumn apply(ChunkColumn col, Op op) {
        SplittableRandom r = new SplittableRandom(op.arg);
        int secs = col.sectionCount();
        int x = r.nextInt(16), z = r.nextInt(16), y = col.minY() + r.nextInt(secs * 16);
        switch (op.kind) {
            case 0 -> col.setBlock(x, y, z, state(r, true));
            case 1 -> col.setGeneratedBlock(x, y, z, state(r, true));
            case 2 -> {   // 배열을 받아 구역에 직접 쓰는 바깥 경로(얼린 구역에는 운영 코드도 직접 쓰지 않는다 — 단언)
                ChunkSection cs = col.sections()[r.nextInt(secs)];
                if (!cs.lightFrozen) cs.setBlock(x, r.nextInt(16), z, state(r, true));
            }
            case 3 -> col.loadSectionBlocks(r.nextInt(secs), flat(r));
            case 4 -> {
                int[] f = flat(r);
                int[] vals = java.util.Arrays.stream(f).distinct().toArray();
                BitStorage idx = new BitStorage(4, 4096);
                for (int i = 0; i < 4096; i++) for (int v = 0; v < vals.length; v++) if (vals[v] == f[i]) { idx.set(i, v); break; }
                col.loadSectionIndexed(r.nextInt(secs), vals, idx);
            }
            case 5 -> {   // 월드젠처럼 scratch 를 열어 직접 고치고 compact
                int s = r.nextInt(secs);
                int[] scr = col.sectionBlocks(s);
                int writes = 1 + r.nextInt(30);
                for (int i = 0; i < writes; i++) scr[r.nextInt(4096)] = state(r, true);
                col.compactSections();
            }
            case 6 -> { return col.copy(); }
            case 7 -> {   // cowCopy 사본에 쓴다 — 원본은 버린다(LiveFeatures 처럼 원본을 바꾸지 않음)
                ChunkColumn c = col.cowCopy();
                if (r.nextBoolean()) c.setBlock(x, y, z, state(r, true));
                return c;
            }
            case 8 -> col.sections()[r.nextInt(secs)].isRandomlyTicking();   // 센 값을 캐시(0 이면 비트가 꺼진다)
            case 9 -> {
                int[] blocks = new int[secs * 4096], biomes = new int[secs * 64];
                for (int s = 0; s < secs; s++) if (r.nextInt(4) == 0) System.arraycopy(flat(r), 0, blocks, s * 4096, 4096);
                col.loadGenerated(blocks, biomes);
            }
            case 10 -> FROZEN.add(col.freezeForLight());
            case 11 -> thawAll();
            default -> throw new IllegalStateException();
        }
        return col;
    }

    /** 브루트: 칸을 모두 읽어 무작위 틱 받는 칸이 있는 구역. 컬럼을 고치지 않게 공유 구역도 읽기 전용 getBlock 으로. */
    private static BitSet brute(ChunkColumn col) {
        BitSet out = new BitSet();
        for (int s = 0; s < col.sectionCount(); s++) {
            if (!col.sectionMaybeHas(s, v -> isTicking[v])) continue;   // 팔레트에 없으면 확실히 없다(ChunkSectionRandomTickingTest 가 따로 지킨다)
            int y0 = col.minY() + s * 16;
            scan:
            for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) for (int x = 0; x < 16; x++) {
                if (isTicking[col.getBlock(x, y0 + y, z)]) { out.set(s); break scan; }
            }
        }
        return out;
    }

    /** 새 길: tickChunk 처럼 마스크로 고른 구역 중 isRandomlyTicking 인 것. 단언 검사도 함께. */
    private static BitSet viaMask(ChunkColumn col) {
        ChunkSection[] secs = col.sections();
        assertTrue(col.randomTickMaskCovers(), "마스크가 무작위 틱 구역을 놓침");
        for (int si = 0; si < Math.min(64, secs.length); si++) {   // 꺼진 비트 구역은 블록을 다시 세어도 0(단언보다 강한 검사)
            if ((col.randomTickMask() & (1L << si)) == 0) assertEquals(0, secs[si].recountTicking(), "구역 " + si);
        }
        BitSet out = new BitSet();
        for (int si = col.nextRandomTickingSection(0); si < secs.length; si = col.nextRandomTickingSection(si + 1)) {
            if (secs[si].isRandomlyTicking()) out.set(si);
        }
        return out;
    }

    private static List<BitSet> run(Input in, java.util.function.Function<ChunkColumn, BitSet> probe) {
        ChunkColumn col = fresh(in.tall);
        List<BitSet> out = new ArrayList<>();
        for (Op op : in.ops) {
            col = apply(col, op);
            out.add(probe.apply(col));
        }
        thawAll();
        return out;
    }

    private static Differential<Input, List<BitSet>> diff() {
        return Differential.<Input, List<BitSet>>of("random-tick-section-mask", RandomTickMaskTest::input)
                .seed(0x7053L).iterations(120).describe(Input::summary);
    }

    @Test
    void maskWalkMatchesBruteForceAcrossWritePaths() {
        diff().check(in -> run(in, RandomTickMaskTest::brute), in -> run(in, RandomTickMaskTest::viaMask));
    }

    /** 변이 점검: 첫 연산 뒤 마스크를 굳혀 쓰는 것(= 나중에 켜진 비트를 놓침)을 생성기가 잡는다. */
    @Test
    void detectsStaleMask() {
        diff().assertDetects(in -> run(in, RandomTickMaskTest::brute), in -> {
            long[] frozen = {Long.MIN_VALUE};
            return run(in, col -> {
                ChunkSection[] secs = col.sections();
                if (frozen[0] == Long.MIN_VALUE) {   // 첫 탐침은 제대로 훑어 센 값을 캐시한 뒤 그 마스크를 굳힌다
                    BitSet first = viaMask(col);
                    frozen[0] = col.randomTickMask();
                    return first;
                }
                BitSet out = new BitSet();
                for (int si = 0; si < secs.length; si++) {
                    if (si < 64 && (frozen[0] & (1L << si)) == 0) continue;
                    if (secs[si].isRandomlyTicking()) out.set(si);
                }
                return out;
            });
        });
    }

    @Test
    void cowCopyKeepsSharedBitsOnAndOriginalUntouched() {
        init();
        ChunkColumn a = fresh(false);
        int tickingState = ticking[0];
        a.setBlock(1, -60, 1, tickingState);
        for (ChunkSection s : a.sections()) s.isRandomlyTicking();
        long before = a.randomTickMask();
        assertEquals(1L, before, "구역 0 만 켜짐");
        ChunkColumn b = a.cowCopy();
        assertEquals((1L << 24) - 1, b.randomTickMask(), "공유 구역 비트는 켜 둔다");
        b.setBlock(1, 100, 1, tickingState);   // 구역 10 을 복사해 가진다
        b.setBlock(1, -60, 1, ChunkSection.AIR);   // 구역 0 을 복사해 가진 뒤 무작위 틱 칸을 지운다
        b.sections();   // 모두 복사해 가진다
        for (ChunkSection s : b.sections()) s.isRandomlyTicking();
        assertEquals(1L << 10, b.randomTickMask());
        assertTrue(b.randomTickMaskCovers());
        assertEquals(before, a.randomTickMask(), "사본 쓰기가 원본 마스크를 바꾸지 않음");
        assertTrue(a.randomTickMaskCovers());
        assertTrue(a.sections()[0].isRandomlyTicking());
    }

    @Test
    void sectionsAbove64AreAlwaysVisited() {
        init();
        ChunkColumn col = fresh(true);
        for (ChunkSection s : col.sections()) s.isRandomlyTicking();
        assertEquals(0L, col.randomTickMask());
        assertEquals(64, col.nextRandomTickingSection(0), "64 이상은 비트 없이 늘 확인");
        assertEquals(69, col.nextRandomTickingSection(69));
        assertEquals(70, col.nextRandomTickingSection(70));
        col.setBlock(0, col.minY() + 65 * 16, 0, ticking[0]);
        assertTrue(col.randomTickMaskCovers());
        assertEquals(0L, col.randomTickMask(), "64 이상 구역은 비트를 건드리지 않음");
    }
}
