// N2b-2 고정 하이트맵 경계(PR #373 검토 R1~R4): 바닐라 jar 프로브 값(검토 보고서 n2b2-pr373.md)과 같은가.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class FixedHeightmapTest {

    private static int stone() {
        BlockBehaviors.ofBlock(0);
        return Blocks.get().defaultState("minecraft:stone");
    }

    /** 바닥 섹션 맨 아래 층은 (0,0) 만 빼고 돌, 기둥 (0,0) 에는 로컬 높이 60·80·100 에 돌(디스크에서 읽은 청크처럼). */
    private static ChunkColumn column() {
        int stone = stone();
        ChunkColumn col = new ChunkColumn(0);
        int[][] secs = new int[24][4096];
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) if (x != 0 || z != 0) secs[0][z * 16 + x] = stone;
        for (int ly : new int[] {60, 80, 100}) secs[ly >> 4][(ly & 15) * 256] = stone;
        for (int si = 0; si < 24; si++) col.loadSectionBlocks(si, secs[si]);
        col.rebuildHeightmapsAfterSurface();
        return col;
    }

    private static int value(ChunkColumn col, int blockY) { return blockY - col.minY() + 1; }

    /** R1 — 바닐라 jar: 섹션 5 개수를 0 으로 대입한 뒤 로컬 100 을 공기로 → 내려가며 섹션 5(로컬 80~95)를 공기로 봄 → 네 종류 모두 61. */
    @Test
    void countAssignmentFixesHeightmapsLikeVanilla() {
        ChunkColumn col = column();
        col.setSectionVanillaCounts(5, 0, 0, 0);
        assertTrue(col.heightmapsFixed(), "개수 대입은 고정 모드로");
        col.setBlock(0, 100 + col.minY(), 0, ChunkSection.AIR);
        assertEquals(61, value(col, col.surfaceBlockY(0, 0)));
        assertEquals(61, value(col, col.motionBlockingBlockY(0, 0)));
        assertEquals(61, value(col, col.oceanFloorBlockY(0, 0)));
        assertEquals(61, value(col, col.motionBlockingNoLeavesBlockY(0, 0)));
    }

    /** R2 — 바닐라 jar: setRawData 로 높이 밖 값(500)을 넣고 그 아래 칸을 공기로 update → 높이 밖은 공기로 읽으며 내려가 101. */
    @Test
    void updateDescendsFromAboveWorld() {
        ChunkColumn col = column();
        long[] raw = col.worldSurfaceHeightmap().clone();
        raw[0] = (raw[0] & ~511L) | 500L;
        assertTrue(col.setHeightmapRaw(HeightmapType.WORLD_SURFACE, raw));
        assertTrue(col.heightmapUpdate(HeightmapType.WORLD_SURFACE, 0, 499 + col.minY(), 0, ChunkSection.AIR));
        assertEquals(101, value(col, col.surfaceBlockY(0, 0)));
    }

    /** R3 — 바닐라 jar: 9비트를 넘는 값으로 올리면 SimpleBitStorage 범위 검사 IAE, 값은 그대로. */
    @Test
    void updateBeyondNineBitsThrowsLikeVanilla() {
        ChunkColumn col = column();
        int before = col.surfaceBlockY(1, 0);
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> col.heightmapUpdate(HeightmapType.WORLD_SURFACE, 1, 600, 0, stone()));
        assertEquals("The value 665 is not in the specified inclusive range of 0 to 511", e.getMessage());
        assertEquals(before, col.surfaceBlockY(1, 0));
    }

    /** R4(M22) — 직접 update 뒤 캐시된 raw(저장·청크 패킷·shim getRawData 가 읽음)도 새 값. */
    @Test
    void directUpdateRefreshesCachedRaw() {
        ChunkColumn col = column();
        long[] cached = col.worldSurfaceHeightmap();
        assertEquals(101, (int) (cached[0] & 511));
        assertTrue(col.heightmapUpdate(HeightmapType.WORLD_SURFACE, 0, 200 + col.minY(), 0, stone()));
        assertEquals(201, (int) (col.worldSurfaceHeightmap()[0] & 511));
    }

    /** R4(M18) — 고정 청크의 사본(LiveGrid·LiveFeatures 가 읽는 copy, 저장·빛 작업의 cowCopy)도 같은 고정 값이고, 원본과 따로 움직인다. */
    @Test
    void copiesKeepFixedHeights() {
        ChunkColumn col = column();
        col.setSectionStateRaw(6, (100 & 15) * 256, ChunkSection.AIR, true);   // 원시 쓰기 — WORLD_SURFACE 는 101 그대로
        assertTrue(col.heightmapsFixed());
        for (ChunkColumn c : new ChunkColumn[] {col.copy(), col.cowCopy()}) {
            assertTrue(c.heightmapsFixed());
            assertEquals(101, value(c, c.surfaceBlockY(0, 0)), "사본도 낡은 값");
            assertArrayEquals(col.worldSurfaceHeightmap(), c.worldSurfaceHeightmap());
            assertArrayEquals(col.motionBlockingHeightmap(), c.motionBlockingHeightmap());
        }
        ChunkColumn c = col.copy();
        c.heightmapUpdate(HeightmapType.WORLD_SURFACE, 0, 300 + col.minY(), 0, stone());
        assertEquals(101, value(col, col.surfaceBlockY(0, 0)), "사본 갱신이 원본에 새지 않음");
    }
}
