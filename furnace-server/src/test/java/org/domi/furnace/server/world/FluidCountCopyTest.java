// PR #358 검토 R3: 양동이 물로 켜진 유체 수가 구역 객체를 갈아 끼우는 길(copy·cowCopy 첫 쓰기·빛 작업 얼림 중 쓰기)을 지나도 남는다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Blocks;
import org.junit.jupiter.api.Test;

class FluidCountCopyTest {

    private static final int SEC = 5;

    private static ChunkColumn withBucketWater() {
        BlockBehaviors.ofBlock(0);
        ChunkColumn col = new ChunkColumn(0);
        int y = col.minY() + (SEC << 4);
        col.setBlock(3, y + 4, 5, Blocks.get().defaultState("minecraft:water"));
        assertTrue(col.sections()[SEC].isRandomlyTicking(), "양동이 물 구역은 대상");
        return col;
    }

    private static void assertTicking(ChunkColumn c, String what) {
        assertTrue(c.randomTickMaskCovers(), what + " 마스크");
        assertEquals(SEC, c.nextRandomTickingSection(SEC), what + " 비트");
        assertTrue(c.sections()[SEC].isRandomlyTicking(), what);
    }

    @Test
    void columnCopyKeepsFluidCount() {
        assertTicking(withBucketWater().copy(), "copy");
    }

    @Test
    void cowCopyFirstWriteKeepsFluidCount() {
        ChunkColumn c = withBucketWater().cowCopy();
        int y = c.minY() + (SEC << 4);
        c.setBlock(9, y + 9, 9, Blocks.get().defaultState("minecraft:stone"));   // 공유 구역을 복사해 가진다
        assertTicking(c, "cowCopy 쓰기 뒤");
    }

    @Test
    void lightFrozenWriteKeepsFluidCount() {
        ChunkColumn c = withBucketWater();
        ChunkColumn.LightBlocks lb = c.freezeForLight();
        int y = c.minY() + (SEC << 4);
        c.setBlock(9, y + 9, 9, Blocks.get().defaultState("minecraft:stone"));   // 얼린 구역 → 복사본으로 갈아 끼움
        lb.thaw();
        assertTicking(c, "얼림 중 쓰기 뒤");
    }
}
