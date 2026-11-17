// T-209: 공개 스냅샷·주인 사본이 섹션 블록 저장소를 서버 배치 그대로(PackedStates) 내보이는가 — 단일 값·간접·직접 저장, 펼친 사본이면 없음.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.domi.furnace.api.internal.ChunkInternals;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SectionPackedStatesTest {

    @BeforeAll
    static void blocks() {
        org.domi.furnace.server.block.BlockBehaviors.ofBlock(0);   // 블록 표(높이 지도·개수가 본다) — ChunkReadViewTest 와 같은 준비
        org.domi.furnace.server.block.Blocks.get();
    }

    /** 사본을 칸 번호로 푼다(바닐라 SimpleBitStorage 배치). */
    static int[] decode(ChunkInternals.PackedStates p) {
        int[] out = new int[4096];
        if (p.bits() == 0) {
            java.util.Arrays.fill(out, p.paletteValue(0));
            return out;
        }
        long[] raw = p.rawCopy();
        int bits = p.bits(), vpl = 64 / bits;
        assertEquals((4096 + vpl - 1) / vpl, raw.length);
        long mask = (1L << bits) - 1;
        for (int i = 0; i < 4096; i++) {
            int v = (int) (raw[i / vpl] >>> ((i % vpl) * bits) & mask);
            out[i] = p.paletteSize() < 0 ? v : p.paletteValue(v);
        }
        return out;
    }

    static void fill(ChunkColumn col, int y0, java.util.function.IntUnaryOperator value) {
        for (int i = 0; i < 4096; i++) col.setBlock(i & 15, y0 + (i >> 8), (i >> 4) & 15, value.applyAsInt(i));
    }

    @Test
    void snapshotAndOwnerCopyMatchSectionForEveryStorage() {
        ChunkColumn col = new ChunkColumn(0);
        int si = 5, y0 = col.minY() + si * 16;
        Random rnd = new Random(209);
        // 단일 값(아무것도 안 쓴 섹션 = 공기)
        ChunkInternals.PackedStates p = col.publishReadView(0, 0).sectionStatesPacked(si);
        assertEquals(0, p.bits());
        assertEquals(1, p.paletteSize());
        assertArrayEquals(col.sections()[si].blocks(), decode(p));
        // 간접: 몇 가지 값 → 4 비트 배열 팔레트
        fill(col, y0, i -> 1 + rnd.nextInt(6));
        p = col.publishReadView(0, 0).sectionStatesPacked(si);
        assertEquals(4, p.bits());
        assertTrue(p.paletteSize() >= 6 && p.paletteSize() <= 16, "팔레트 " + p.paletteSize());
        assertArrayEquals(col.sections()[si].blocks(), decode(p));
        // 간접 5~8 비트
        fill(col, y0, i -> 1 + rnd.nextInt(40));
        p = col.publishReadView(0, 0).sectionStatesPacked(si);
        assertTrue(p.bits() >= 6 && p.bits() <= 8, "비트 " + p.bits());
        assertArrayEquals(col.sections()[si].blocks(), decode(p));
        // 직접: 서로 다른 값이 많이 지나간 뒤 몇 가지로 — 팔레트는 줄지 않는다
        fill(col, y0, i -> 1 + i);
        fill(col, y0, i -> 1 + rnd.nextInt(6));
        ChunkReadView v = col.publishReadView(0, 0);
        p = v.sectionStatesPacked(si);
        assertEquals(-1, p.paletteSize());
        assertEquals(15, p.bits());
        int[] want = col.sections()[si].blocks();
        assertArrayEquals(want, decode(p));
        long[] a = p.rawCopy();
        a[0] = ~a[0];
        assertArrayEquals(want, decode(p), "받은 사본을 고쳐도 스냅샷은 그대로");
        // 주인 사본(살아 있는 섹션)도 같은 꼴, 그 뒤 쓰기에 흔들리지 않는다
        ChunkInternals.PackedStates own = col.sections()[si].blocksPacked();
        assertEquals(p.bits(), own.bits());
        assertArrayEquals(p.rawCopy(), own.rawCopy());
        col.setBlock(0, y0, 0, 7);
        assertEquals(want[0], decode(own)[0], "사본은 뒤 쓰기와 무관");
        assertEquals(want[0], decode(v.sectionStatesPacked(si))[0], "낸 판은 뒤 쓰기와 무관");
    }

    @Test
    void openScratchHasNoPackedCopy() {
        ChunkColumn col = new ChunkColumn(0);
        int si = 3;
        col.sectionBlocks(si)[0] = 2;   // 펼친 배열을 연 채로
        assertNull(col.sections()[si].blocksPacked());
        ChunkReadView v = col.publishReadView(0, 0);
        assertNull(v.sectionStatesPacked(si));
        assertEquals(2, v.sectionStates(si)[0]);
    }
}
