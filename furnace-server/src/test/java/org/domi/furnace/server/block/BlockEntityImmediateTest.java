// T-198: ChunkLevel.getBlockEntity 는 바닐라 Level#getBlockEntity(IMMEDIATE) — 블록 엔티티가 빠진 칸(통 등)에 빈 것을 새로 만든다. CHECK 는 만들지 않는다.
// (상자는 RegionLevel 이 자기 틀로 만든다 — 실서버 경로는 호환 시험 NmsBlockEntityWriteTest 가 본다.)
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.block.entity.BlockEntity;
import org.junit.jupiter.api.Test;

class BlockEntityImmediateTest {

    static final Pos POS = new Pos(2, -58, 3);

    @Test
    void immediateRecreatesMissingBlockEntity() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(POS, B.defaultState("minecraft:barrel"), 3);
        Object first = level.getBlockEntity(POS);
        assertInstanceOf(BlockEntity.class, first);

        level.removeBlockEntity(POS);   // FAWE: 칸은 통 그대로, 블록 엔티티만 뺀다
        assertTrue(((BlockEntity) first).isRemoved());
        assertNull(level.getBlockEntityIfPresent(POS), "CHECK 는 만들지 않는다");
        assertEquals(B.defaultState("minecraft:barrel"), level.getBlockState(POS));

        Object again = level.getBlockEntity(POS);
        assertInstanceOf(BlockEntity.class, again);
        assertNotSame(first, again);
        assertFalse(((BlockEntity) again).isRemoved());
        assertSame(again, level.getBlockEntityIfPresent(POS), "만든 것은 청크에 등록된다");
    }

    @Test
    void blocksWithoutBlockEntityStayEmpty() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(POS, B.defaultState("minecraft:stone"), 3);
        assertNull(level.getBlockEntity(POS));
        assertNull(level.getBlockEntity(new Pos(2, 100000, 3)), "높이 밖");
    }

    /** 아직 해석하지 않은 생성 NBT(서버 틀이 없는 종류)가 있는 칸은 새로 만들어 덮지 않는다(바닐라 pendingBlockEntities 를 먼저 쓴다). */
    @Test
    void unparsedGeneratedNbtIsNotOverwritten() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(POS, B.defaultState("minecraft:spawner"), 3);
        level.removeBlockEntity(POS);
        var col = level.chunk(POS.x() >> 4, POS.z() >> 4);
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:mob_spawner");
        col.putGeneratedBlockEntity(POS.x() & 15, POS.y(), POS.z() & 15, nbt);
        assertNull(level.getBlockEntity(POS));
        assertSame(nbt, col.generatedBlockEntity(POS.x() & 15, POS.y(), POS.z() & 15));
    }
}
