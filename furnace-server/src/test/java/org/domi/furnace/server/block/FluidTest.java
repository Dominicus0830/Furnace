package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** 유체 흐름(FlowingFluid) — 기대값은 바닐라 규칙(물 감소량 1, 찾는 거리 4, 구멍 쪽 우선, 원천 둘 + 단단한 바닥 = 새 원천)으로 손 계산. */
class FluidTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();
    static final int AIR = B.defaultState("minecraft:air");
    static final int WATER = B.defaultState("minecraft:water");
    static final int LAVA = B.defaultState("minecraft:lava");

    private static void run(BlockPlacementTest.TestLevel level, int ticks) {
        for (int i = 0; i < ticks; i++) level.runScheduledTicks();
    }

    private static String at(BlockPlacementTest.TestLevel level, int x, int y, int z) { return B.describe(level.getBlockState(new Pos(x, y, z))); }

    private static String water(int legacyLevel) { return "minecraft:water[level=" + legacyLevel + "]"; }

    @Test
    void waterSpreadsOnFlatGround_andRetractsWhenSourceRemoved() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), WATER, 3);
        run(level, 100);
        assertEquals(water(0), at(level, 0, -60, 0));
        assertEquals(water(3), at(level, 3, -60, 0));
        assertEquals(water(7), at(level, -7, -60, 0));
        assertEquals(water(4), at(level, 2, -60, 2));
        assertEquals(water(7), at(level, 3, -60, -4));
        assertEquals("minecraft:air", at(level, 8, -60, 0));
        assertEquals("minecraft:air", at(level, 4, -60, 4));
        assertEquals("minecraft:air", at(level, 0, -59, 0));

        level.setBlock(new Pos(0, -60, 0), AIR, 3);
        run(level, 300);
        for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
            assertEquals("minecraft:air", at(level, x, -60, z), x + "," + z);
        }
    }

    @Test
    void twoSourcesOnSolidGround_makeNewSource() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), WATER, 3);
        level.setBlock(new Pos(2, -60, 0), WATER, 3);
        run(level, 40);
        assertEquals(water(0), at(level, 1, -60, 0));
        // 원천 하나만 옆에 있는 칸은 흐름
        assertEquals(water(1), at(level, 1, -60, 1));
    }

    @Test
    void flowPrefersNearestHole() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(2, -61, 0), AIR, 3);   // 동쪽 두 칸 너머 구멍
        level.setBlock(new Pos(0, -60, 0), WATER, 3);
        run(level, 60);
        assertEquals(water(1), at(level, 1, -60, 0));
        assertEquals(water(2), at(level, 2, -60, 0));
        assertEquals(water(8), at(level, 2, -61, 0));   // 떨어지는 흐름
        assertEquals("minecraft:air", at(level, -1, -60, 0));
        assertEquals("minecraft:air", at(level, 0, -60, 1));
        assertEquals("minecraft:air", at(level, 0, -60, -1));
    }

    @Test
    void lavaFallingOntoWater_turnsItToStone() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), WATER, 3);
        run(level, 60);
        level.setBlock(new Pos(0, -58, 0), LAVA, 3);
        run(level, 200);
        assertEquals("minecraft:stone", at(level, 0, -60, 0));
        assertEquals("minecraft:lava[level=8]", at(level, 0, -59, 0));
    }

    @Test
    void lavaSpreadsTwoPerBlock_andNeverMakesSourcesByDefault() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -60, 0), LAVA, 3);
        level.setBlock(new Pos(2, -60, 0), LAVA, 3);
        run(level, 600);
        // 용암 감소량 2: 옆 칸 양 6(level 2), 그다음 4(level 4), 2(level 6)
        assertEquals("minecraft:lava[level=2]", at(level, 1, -60, 0));
        assertEquals("minecraft:lava[level=2]", at(level, -1, -60, 0));
        assertEquals("minecraft:lava[level=4]", at(level, -2, -60, 0));
        assertEquals("minecraft:lava[level=6]", at(level, -3, -60, 0));
        assertEquals("minecraft:air", at(level, -4, -60, 0));
    }

    @Test
    void flowingWaterDoesNotEnterWaterloggableBlocks_butSourceWaterlogsCampfire() {
        var level = new BlockPlacementTest.TestLevel();
        int fence = B.defaultState("minecraft:oak_fence");
        level.setBlock(new Pos(1, -60, 0), fence, 3);
        level.setBlock(new Pos(0, -60, 0), WATER, 3);
        run(level, 60);
        assertEquals(fence, level.getBlockState(new Pos(1, -60, 0)), "흐르는 물은 울타리를 잠그지 않는다");
        assertEquals(water(4), at(level, 2, -60, 0), "울타리를 돌아서(맨해튼 4) 온다");

        Pos camp = new Pos(5, -60, 5);
        level.setBlock(camp, B.defaultState("minecraft:campfire"), 3);
        int s = level.getBlockState(camp);
        assertTrue(BlockBehaviors.of(s).placeLiquid(level, camp, s, Fluids.WATER_SOURCE));
        assertEquals("minecraft:campfire[facing=north,lit=false,signal_fire=false,waterlogged=true]", at(level, 5, -60, 5));
    }

    @Test
    void mergedFaceOccludes_unionOfBothFaces() {
        Shape bottom = new Shape(new double[] {0, 0, 0, 1, 0.5, 1});
        Shape top = new Shape(new double[] {0, 0.5, 0, 1, 1, 1});
        assertTrue(Shape.mergedFaceOccludes(Shape.BLOCK, Shape.BLOCK, Dir.EAST));
        assertFalse(Shape.mergedFaceOccludes(Shape.EMPTY, Shape.EMPTY, Dir.EAST));
        assertFalse(Shape.mergedFaceOccludes(bottom, bottom, Dir.EAST));
        assertTrue(Shape.mergedFaceOccludes(bottom, top, Dir.EAST));
        assertTrue(Shape.mergedFaceOccludes(top, Shape.EMPTY, Dir.UP));
        assertFalse(Shape.mergedFaceOccludes(bottom, Shape.EMPTY, Dir.UP));
        assertTrue(Shape.mergedFaceOccludes(Shape.EMPTY, bottom, Dir.UP));
    }
}
