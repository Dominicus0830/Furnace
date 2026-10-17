package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.entity.ComparatorBlockEntity;
import org.domi.furnace.server.block.entity.PistonMovingBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 피스톤(PistonBaseBlock·PistonStructureResolver·PistonMovingBlockEntity)과 블록 엔티티 틀 — 바닐라 규칙으로 손 계산. */
class PistonTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    /** 서버 틱 한 번의 블록 부분: 예약 틱 → 블록 이벤트 → 블록 엔티티. */
    private static void step(BlockPlacementTest.TestLevel level, int n) {
        for (int i = 0; i < n; i++) {
            level.runScheduledTicks();
            level.runBlockEvents();
            level.tickBlockEntities(k -> true);
        }
    }

    private static String at(BlockPlacementTest.TestLevel level, int x, int y, int z) { return B.describe(level.getBlockState(new Pos(x, y, z))); }

    private static int piston(String name, String facing) {
        int s = B.defaultState(name);
        return B.prop("facing").with(s, facing);
    }

    @Test
    void piston_pushesBlock_andRetracts() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        level.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:stone"), 3);
        level.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        level.runBlockEvents();
        assertEquals("minecraft:moving_piston[facing=east,type=normal]", at(level, 1, -60, 0));
        assertEquals("minecraft:moving_piston[facing=east,type=normal]", at(level, 2, -60, 0));
        assertInstanceOf(PistonMovingBlockEntity.class, level.getBlockEntity(new Pos(2, -60, 0)));
        level.tickBlockEntities(k -> true);   // 같은 틱: 0 → 0.5
        step(level, 2);                       // 0.5 → 1, 그다음 틱에 놓는다
        assertEquals("minecraft:piston[extended=true,facing=east]", at(level, 0, -60, 0));
        assertEquals("minecraft:piston_head[facing=east,short=false,type=normal]", at(level, 1, -60, 0));
        assertEquals("minecraft:stone", at(level, 2, -60, 0));
        assertNull(level.getBlockEntity(new Pos(2, -60, 0)));

        level.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:air"), 3);
        step(level, 4);
        assertEquals("minecraft:piston[extended=false,facing=east]", at(level, 0, -60, 0));
        assertEquals("minecraft:air", at(level, 1, -60, 0));
        assertEquals("minecraft:stone", at(level, 2, -60, 0), "보통 피스톤은 당기지 않는다");
    }

    @Test
    void stickyPiston_pullsBlockBack() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), piston("minecraft:sticky_piston", "east"), 3);
        level.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:stone"), 3);
        level.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(level, 4);
        assertEquals("minecraft:stone", at(level, 2, -60, 0));
        level.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:air"), 3);
        step(level, 4);
        assertEquals("minecraft:sticky_piston[extended=false,facing=east]", at(level, 0, -60, 0));
        assertEquals("minecraft:stone", at(level, 1, -60, 0));
        assertEquals("minecraft:air", at(level, 2, -60, 0));
    }

    @Test
    void slimeBlock_dragsAttachedBlocks() {
        // 공중에서(바닥에 닿으면 슬라임이 땅까지 끌어 12개를 넘는다)
        var level = new BlockPlacementTest.TestLevel();
        int y = -55;
        level.setBlock(new Pos(0, y, 0), piston("minecraft:piston", "east"), 3);
        level.setBlock(new Pos(1, y, 0), B.defaultState("minecraft:slime_block"), 3);
        level.setBlock(new Pos(1, y + 1, 0), B.defaultState("minecraft:stone"), 3);       // 슬라임 위
        level.setBlock(new Pos(1, y, 1), B.defaultState("minecraft:honey_block"), 3);     // 슬라임과 꿀은 붙지 않는다
        level.setBlock(new Pos(-1, y, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(level, 4);
        assertEquals("minecraft:slime_block", at(level, 2, y, 0));
        assertEquals("minecraft:stone", at(level, 2, y + 1, 0));
        assertEquals("minecraft:air", at(level, 1, y + 1, 0));
        assertEquals("minecraft:honey_block", at(level, 1, y, 1));
    }

    @Test
    void slimeOnGround_exceedsPushLimit() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        level.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:slime_block"), 3);
        level.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(level, 4);
        assertEquals("minecraft:piston[extended=false,facing=east]", at(level, 0, -60, 0), "슬라임이 잔디 줄을 끌어 12개를 넘는다");
    }

    @Test
    void pushLimitAndImmovableBlocks() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        for (int x = 1; x <= 13; x++) level.setBlock(new Pos(x, -60, 0), B.defaultState("minecraft:stone"), 3);
        level.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(level, 4);
        assertEquals("minecraft:piston[extended=false,facing=east]", at(level, 0, -60, 0), "13개는 못 민다");

        var l2 = new BlockPlacementTest.TestLevel();
        l2.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        l2.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:obsidian"), 3);
        l2.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(l2, 4);
        assertEquals("minecraft:piston[extended=false,facing=east]", at(l2, 0, -60, 0));

        // 부서지는 블록(destroy)은 밀리지 않고 부서진다
        var l3 = new BlockPlacementTest.TestLevel();
        l3.setBlock(new Pos(0, -60, 0), piston("minecraft:piston", "east"), 3);
        l3.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:stone"), 3);
        l3.setBlock(new Pos(2, -60, 0), B.defaultState("minecraft:pumpkin"), 3);
        l3.setBlock(new Pos(-1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(l3, 4);
        assertEquals("minecraft:stone", at(l3, 2, -60, 0));
        assertEquals("minecraft:air", at(l3, 3, -60, 0));
    }

    @Test
    void movingBlockEntity_nbtRoundTrip() {
        var be = new PistonMovingBlockEntity(new Pos(1, 2, 3), B.defaultState("minecraft:moving_piston"),
                B.prop("facing").with(B.defaultState("minecraft:observer"), "up"), Dir.SOUTH, true, false);
        NbtCompound tag = be.saveWithFullMetadata();
        assertEquals("minecraft:piston", tag.getString("id"));
        assertEquals(3, tag.getIntOr0("facing"));
        var back = (PistonMovingBlockEntity) org.domi.furnace.server.block.entity.BlockEntities.loadStatic(new Pos(1, 2, 3),
                B.defaultState("minecraft:moving_piston"), tag);
        assertEquals(be.getMovedState(), back.getMovedState());
        assertEquals(Dir.SOUTH, back.getDirection());
        assertTrue(back.isExtending());
    }

    @Test
    void comparator_storesOutputInBlockEntity() {
        var level = new BlockPlacementTest.TestLevel();
        Pos cmp = new Pos(0, -60, 0);
        int c = B.prop("facing").with(B.defaultState("minecraft:comparator"), "east");   // 입력은 facing 쪽(동쪽), 출력은 서쪽
        level.setBlock(cmp, c, 3);
        assertInstanceOf(ComparatorBlockEntity.class, level.getBlockEntity(cmp));
        level.setBlock(new Pos(1, -60, 0), B.defaultState("minecraft:redstone_block"), 3);
        step(level, 3);
        assertEquals(15, ((ComparatorBlockEntity) level.getBlockEntity(cmp)).getOutputSignal());
        assertTrue(B.prop("powered").is(level.getBlockState(cmp)));
    }
}
