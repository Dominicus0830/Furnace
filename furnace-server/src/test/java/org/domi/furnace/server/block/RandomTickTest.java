package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.world.ChunkSection;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 무작위 틱(ServerLevel.tickChunk)·구역 isRandomlyTicking·예약 틱 저장(block_ticks) 왕복. */
class RandomTickTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    private static int count(BlockPlacementTest.TestLevel level, int state, int y0) {
        int n = 0;
        for (int x = 0; x < 16; x++) for (int y = y0; y < y0 + 16; y++) for (int z = 0; z < 16; z++) {
            if (level.getBlockState(new Pos(x, y, z)) == state) n++;
        }
        return n;
    }

    @Test
    void section_tracksRandomlyTickingBlocks() {
        var level = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(3, 5, 3);   // 구역 y 0..15 — 평지에선 공기뿐
        ChunkSection sec = level.chunk(0, 0).sections()[(5 >> 4) - (level.chunk(0, 0).minY() >> 4)];
        assertFalse(sec.isRandomlyTicking());
        level.setBlock(pos, B.defaultState("minecraft:stone"), 2);
        assertFalse(sec.isRandomlyTicking());
        level.setBlock(pos, B.defaultState("minecraft:wheat"), 2);
        assertTrue(sec.isRandomlyTicking());
        level.setBlock(pos, B.defaultState("minecraft:air"), 2);
        assertFalse(sec.isRandomlyTicking());
    }

    private static int randValue(ChunkLevel level) throws Exception {
        var f = ChunkLevel.class.getDeclaredField("randValue");
        f.setAccessible(true);
        return f.getInt(level);
    }

    /**
     * 바닐라: 양동이로 물을 놓은 구역은 isRandomlyTicking(tickingFluidCount 가 물도 센다)이라 tickChunk 가 그 구역에서 칸을 randomTickSpeed 번 뽑는다 —
     * 뽑힌 칸이 물이라 아무 일도 없어도 randValue 가 그만큼 나아간다(다른 구역 칸 선택에 이어진다). 물 없는 구역은 뽑지 않는다.
     */
    @Test
    void tickChunk_bucketWaterSectionDrawsRandomPositions() throws Exception {
        BlockBehaviors.ofBlock(0);   // 무작위 틱·유체 표를 구역에 건다(없으면 구역이 아무것도 세지 않는다)
        var level = new BlockPlacementTest.TestLevel();
        var col = level.chunk(0, 0);
        int speed = 3, start = 12345;
        var rv = ChunkLevel.class.getDeclaredField("randValue");
        rv.setAccessible(true);
        rv.setInt(level, start);
        level.randomTickSections(col, 0, 0, speed);   // 바탕: 평지의 다른 구역(풀 블록 등)이 뽑는 만큼
        int base = randValue(level);
        int baseSteps = -1, v = start;
        for (int k = 0; k <= 400 && baseSteps < 0; k++, v = v * 3 + 1013904223) if (v == base) baseSteps = k;
        assertTrue(baseSteps >= 0, "바탕 뽑기 수를 못 찾음");
        level.setBlock(new Pos(3, 5, 3), B.defaultState("minecraft:water"), 2);   // 구역 y 0..15 는 평지에서 공기뿐
        rv.setInt(level, start);
        level.randomTickSections(col, 0, 0, speed);
        int expect = start;
        for (int i = 0; i < baseSteps + speed; i++) expect = expect * 3 + 1013904223;
        assertEquals(expect, randValue(level), "물을 놓은 구역이 칸을 randomTickSpeed 번 더 뽑는다");
        level.setBlock(new Pos(3, 5, 3), B.defaultState("minecraft:air"), 2);
        rv.setInt(level, start);
        level.randomTickSections(col, 0, 0, speed);
        assertEquals(base, randValue(level), "물을 치우면 다시 뽑지 않음");
    }

    @Test
    void tickChunk_decaysUnconnectedLeaves() {
        var level = new BlockPlacementTest.TestLevel();
        int leaves = B.defaultState("minecraft:oak_leaves");   // 기본값이 distance=7, persistent=false
        for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
            level.setBlock(new Pos(x, y, z), leaves, 2 | 16);
        }
        assertEquals(4096, count(level, leaves, 0));
        // 속도 3 × 40 틱 = 120 칸 뽑기 — 뽑힌 잎은 바로 사라진다
        for (int t = 0; t < 40; t++) level.tickChunk(0, 0, 3);
        int left = count(level, leaves, 0);
        assertTrue(left < 4096 && left >= 4096 - 120, "남은 잎 " + left);
        // 속도 0 이면 무작위 틱 없음
        for (int t = 0; t < 40; t++) level.tickChunk(0, 0, 0);
        assertEquals(left, count(level, leaves, 0));
    }

    @Test
    void scheduledTicks_roundTripThroughChunkNbt() {
        var a = new BlockPlacementTest.TestLevel();
        Pos pos = new Pos(4, -60, 4);
        int sand = B.block("minecraft:sand");
        a.scheduleTick(pos, sand, 5);
        a.runScheduledTicks();
        a.runScheduledTicks();
        assertTrue(a.hasChunkTicks(0, 0));
        NbtCompound root = new NbtCompound();
        a.saveChunkTicks(0, 0, root, true);
        assertFalse(a.hasChunkTicks(0, 0));
        NbtList list = root.getList("block_ticks");
        assertEquals(1, list.items().size());
        NbtCompound e = (NbtCompound) list.items().get(0);
        assertEquals("minecraft:sand", e.getString("i"));
        assertEquals(3, e.getIntOr0("t"));
        assertEquals(0, root.getList("fluid_ticks").items().size());

        var b = new BlockPlacementTest.TestLevel();
        b.loadChunkTicks(root, pos.x() >> 4, pos.z() >> 4);
        assertFalse(root.contains("block_ticks"));
        assertTrue(b.hasScheduledTick(pos, sand));
        b.runScheduledTicks();
        b.runScheduledTicks();
        assertTrue(b.hasScheduledTick(pos, sand));
        b.runScheduledTicks();
        assertFalse(b.hasScheduledTick(pos, sand), "남은 3 틱 뒤에 실행된다");
    }
}
