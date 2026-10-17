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
        b.loadChunkTicks(root);
        assertFalse(root.contains("block_ticks"));
        assertTrue(b.hasScheduledTick(pos, sand));
        b.runScheduledTicks();
        b.runScheduledTicks();
        assertTrue(b.hasScheduledTick(pos, sand));
        b.runScheduledTicks();
        assertFalse(b.hasScheduledTick(pos, sand), "남은 3 틱 뒤에 실행된다");
    }
}
