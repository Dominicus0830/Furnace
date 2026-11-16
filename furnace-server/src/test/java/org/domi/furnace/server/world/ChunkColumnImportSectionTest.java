// N2b-4 거울 들여오기 단일 입구 ChunkColumn.importSection: 바닐라가 하지 않는 일(저장 표시·하이트맵 재계산)은 하지 않고 Furnace 파생 캐시는 모두 맞추는가.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class ChunkColumnImportSectionTest {

    private static int stone() {
        BlockBehaviors.ofBlock(0);
        return Blocks.get().defaultState("minecraft:stone");
    }

    /** 맨 아래 층 돌 + 기둥 (0,0) 로컬 높이 60 에 돌(디스크에서 읽은 청크처럼, 저장 표시 없음). */
    private static ChunkColumn column() {
        int stone = stone();
        ChunkColumn col = new ChunkColumn(0);
        int[][] secs = new int[24][4096];
        for (int i = 0; i < 256; i++) secs[0][i] = stone;
        secs[60 >> 4][(60 & 15) * 256] = stone;
        for (int si = 0; si < 24; si++) col.loadSectionBlocks(si, secs[si]);
        col.rebuildHeightmapsAfterSurface();
        col.clearDirty();
        return col;
    }

    private static int cell(int x, int y, int z) { return y * 256 + z * 16 + x; }

    /** 블록만 들여오면: 칸은 새 값, 바닐라 개수는 옛 값(바닐라는 컨테이너 data 를 갈아도 다시 세지 않는다), 하이트맵은 낡은 값으로 고정, 저장 표시 없음. */
    @Test
    void statesOnlyKeepsCountsAndFixesHeightmaps() {
        ChunkColumn col = column();
        int stone = stone();
        int[] countsBefore = col.sectionVanillaCounts(7);
        assertArrayEquals(new int[] {0, 0, 0}, countsBefore, "높은 섹션은 비었다");
        int surfaceBefore = col.surfaceBlockY(2, 3);
        long mod = col.modCount();
        col.cachedSerializedBody(new byte[] {1});
        int[] states = new int[4096];
        states[cell(2, 5, 3)] = stone;
        col.importSection(7, states, null, null);
        int y = col.minY() + 7 * 16 + 5;
        assertEquals(stone, col.getBlock(2, y, 3));
        assertArrayEquals(countsBefore, col.sectionVanillaCounts(7), "바닐라 개수는 그대로(다시 세지 않음)");
        assertTrue(col.sectionVanillaHasOnlyAir(y), "그래서 바닐라 hasOnlyAir 는 참(바닐라 그대로)");
        assertFalse(col.sectionHasOnlyAir(y), "Furnace 정확한 비공기 수는 1");
        assertEquals(1, col.sectionForRead(7).nonAirCount());
        assertTrue(col.heightmapsFixed(), "섹션을 갈면 하이트맵은 낡은 값을 든다");
        assertEquals(surfaceBefore, col.surfaceBlockY(2, 3));
        assertFalse(col.dirty(), "저장 표시를 세우지 않는다(D3)");
        assertTrue(col.importedUnsaved(), "저장 표시 없이 바뀐 내용");
        assertTrue(col.modCount() > mod);
        assertNull(col.cachedSerializedBody(), "직렬화 본문 캐시를 버린다");
        col.clearDirty();
        assertFalse(col.importedUnsaved(), "저장하면 내린다");
    }

    /** 블록과 개수를 함께 들여오면 개수는 넘긴 값(FAWE 가 필드에 둔 값 — 음수 포함). */
    @Test
    void statesWithCountsTakeGivenCounts() {
        ChunkColumn col = column();
        int[] states = new int[4096];
        states[0] = stone();
        col.importSection(3, states, null, new int[] {1, 0, 0});
        assertArrayEquals(new int[] {1, 0, 0}, col.sectionVanillaCounts(3));
        col.importSection(3, null, null, new int[] {-2, 5, 0});
        assertArrayEquals(new int[] {-2, 5, 0}, col.sectionVanillaCounts(3), "short 범위 값 그대로");
        assertEquals(stone(), col.getBlock(0, col.minY() + 48, 0), "개수만 들여오면 블록은 그대로");
    }

    /** 개수만: 무작위 틱 마스크가 따라가고, 저장할 내용 변경이 아니다(개수는 저장되지 않는다). */
    @Test
    void countsOnlyMoveRandomTickMaskWithoutUnsaved() {
        ChunkColumn col = column();
        col.sectionVanillaCounts(10);   // 센 값으로 비트를 정해 둔다
        assertEquals(0, col.randomTickMask() & (1L << 10), "빈 섹션은 무작위 틱 없음");
        col.importSection(10, null, null, new int[] {5, 3, 0});
        assertNotEquals(0, col.randomTickMask() & (1L << 10), "tickingBlockCount > 0 → 비트 켬");
        assertTrue(col.randomTickMaskCovers());
        col.importSection(10, null, null, new int[] {0, 0, 0});
        assertEquals(0, col.randomTickMask() & (1L << 10));
        assertFalse(col.importedUnsaved());
        assertFalse(col.dirty());
        assertTrue(col.heightmapsFixed(), "nonEmpty 0 섹션은 바닐라 하이트맵 갱신이 공기로 훑는다 — 고정");
    }

    /** 바이옴만: 하이트맵은 블록에서 계속 센다(고정하지 않음), 저장 표시 없는 변경. */
    @Test
    void biomesOnly() {
        ChunkColumn col = column();
        int[] biomes = new int[64];
        biomes[1 * 16 + 2 * 4 + 3] = 1;
        col.importSection(4, null, biomes, null);
        assertEquals(1, col.sectionForRead(4).getBiome(3, 1, 2));
        assertEquals(0, col.sectionForRead(4).getBiome(0, 0, 0));
        assertFalse(col.heightmapsFixed());
        assertTrue(col.importedUnsaved());
    }

    /** 쓰기 시 복사 사본·빛 작업이 쥔 섹션은 그대로(들여오기도 ownSection 을 거친다). */
    @Test
    void copyOnWriteAndLightFrozenSectionsAreNotTouched() {
        ChunkColumn col = column();
        int stone = stone();
        ChunkColumn cow = col.cowCopy();
        int[] air = new int[4096];
        cow.importSection(0, air, null, null);
        assertEquals(0, cow.getBlock(1, col.minY(), 1));
        assertEquals(stone, col.getBlock(1, col.minY(), 1), "원본은 그대로");

        ChunkColumn.LightBlocks frozen = col.freezeForLight();
        col.importSection(0, air, null, null);
        assertEquals(stone, frozen.state(1, col.minY(), 1), "빛 작업이 읽는 옛 섹션은 그대로");
        assertEquals(0, col.getBlock(1, col.minY(), 1));
        frozen.thaw();
    }

    /** 공개 스냅샷: 들여온 섹션만 새 판(나머지 섹션 보기는 재사용), 내용 표시가 바뀐다. */
    @Test
    void publishedViewSeesImport() {
        ChunkColumn col = column();
        ChunkReadView v1 = col.publishReadView(0, 0);
        int[] states = new int[4096];
        states[cell(4, 4, 4)] = stone();
        col.importSection(9, states, null, null);
        ChunkReadView v2 = col.publishReadView(0, 0);
        assertNotSame(v1, v2);
        assertTrue(v2.contentStamp() > v1.contentStamp());
        assertEquals(stone(), v2.stateId(4, col.minY() + 9 * 16 + 4, 4));
        assertSame(v1.section(0), v2.section(0), "바뀌지 않은 섹션 보기는 그대로");
        assertNotSame(v1.section(9), v2.section(9));
    }

    /** 원시 쓰기도 값이 바뀌었을 때만 저장 표시 없는 변경으로 센다. */
    @Test
    void rawWritesMarkImportedUnsavedOnlyOnChange() {
        ChunkColumn col = column();
        col.setSectionStateRaw(0, 0, stone(), true);   // 같은 값
        assertFalse(col.importedUnsaved());
        col.setSectionStateRaw(0, 0, 0, true);
        assertTrue(col.importedUnsaved());
        col.clearDirty();
        col.setSectionBiomeRaw(0, 0, 0);
        assertFalse(col.importedUnsaved());
        col.setSectionBiomeRaw(0, 0, 1);
        assertTrue(col.importedUnsaved());
    }

    @Test
    void rejectsWrongLengths() {
        ChunkColumn col = column();
        assertThrows(IllegalArgumentException.class, () -> col.importSection(0, new int[10], null, null));
        assertThrows(IllegalArgumentException.class, () -> col.importSection(0, null, new int[63], null));
        assertThrows(IllegalArgumentException.class, () -> col.importSection(0, null, null, new int[2]));
        long mod = col.modCount();
        col.importSection(0, null, null, null);
        assertEquals(mod, col.modCount(), "아무것도 없으면 아무것도 안 한다");
    }
}
