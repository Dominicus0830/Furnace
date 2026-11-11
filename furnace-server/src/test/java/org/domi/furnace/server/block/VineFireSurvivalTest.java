// 덩굴·불의 붙어 있을 곳 규칙(바닐라 VineBlock.updateShape·FireBlock.canSurvive): 지지하던 블록이 사라지면 끊기고, 다른 지지가 남아 있으면 면만 줄어든다.
// 기대값은 바닐라 클래스를 손으로 따라간 것이다. 무작위 틱(번짐)은 건드리지 않아 결정적이다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VineFireSurvivalTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static final Blocks B = Blocks.get();

    private static String at(BlockPlacementTest.TestLevel level, Pos p) { return B.describe(level.getBlockState(p)); }

    private static int vine(String... faces) {
        int s = B.defaultState("minecraft:vine");
        for (String f : faces) s = B.prop(f).with(s, "true");
        return s;
    }

    @Test
    void vineBreaksWhenItsOnlySupportIsRemoved() {
        var level = new BlockPlacementTest.TestLevel();
        Pos wall = new Pos(0, -60, 0), v = new Pos(1, -60, 0);
        level.setBlock(wall, B.defaultState("minecraft:stone"), 3);
        level.setBlock(v, vine("west"), 3);
        assertEquals("minecraft:vine[east=false,north=false,south=false,up=false,west=true]", at(level, v));
        level.setBlock(wall, B.defaultState("minecraft:air"), 3);
        assertEquals("minecraft:air", at(level, v), "붙을 면이 하나도 없으면 사라진다");
    }

    @Test
    void vineKeepsRemainingSupportedFace() {
        var level = new BlockPlacementTest.TestLevel();
        Pos west = new Pos(0, -60, 0), north = new Pos(1, -60, -1), v = new Pos(1, -60, 0);
        level.setBlock(west, B.defaultState("minecraft:stone"), 3);
        level.setBlock(north, B.defaultState("minecraft:stone"), 3);
        level.setBlock(v, vine("west", "north"), 3);
        level.setBlock(west, B.defaultState("minecraft:air"), 3);
        assertEquals("minecraft:vine[east=false,north=true,south=false,up=false,west=false]", at(level, v));
    }

    @Test
    void sideFaceHeldByVineAbove() {
        var level = new BlockPlacementTest.TestLevel();
        Pos wall = new Pos(0, -59, 0), upper = new Pos(1, -59, 0), lower = new Pos(1, -60, 0);
        level.setBlock(wall, B.defaultState("minecraft:stone"), 3);
        level.setBlock(upper, vine("west"), 3);
        level.setBlock(lower, vine("west"), 3);
        // 아래 덩굴은 옆 블록이 없어도 위 덩굴이 같은 면을 가져 붙어 있다(canSupportAtFace)
        assertEquals("minecraft:vine[east=false,north=false,south=false,up=false,west=true]", at(level, lower));
        level.setBlock(wall, B.defaultState("minecraft:air"), 3);
        assertEquals("minecraft:air", at(level, upper));
    }

    @Test
    void fireNeedsSolidGroundOrFlammableNeighbour() {
        var level = new BlockPlacementTest.TestLevel();
        Pos ground = new Pos(5, -61, 5), fire = new Pos(5, -60, 5);
        level.setBlock(ground, B.defaultState("minecraft:stone"), 3);
        level.setBlock(fire, B.defaultState("minecraft:fire"), 3);
        assertTrue(at(level, fire).startsWith("minecraft:fire"), at(level, fire));
        level.setBlock(ground, B.defaultState("minecraft:air"), 3);
        assertEquals("minecraft:air", at(level, fire), "받침이 사라지고 불붙는 이웃도 없으면 꺼진다");
    }
}
