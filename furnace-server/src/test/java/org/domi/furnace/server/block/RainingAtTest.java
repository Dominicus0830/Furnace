package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Level.isRainingAt: 비 + 하늘 노출 + MOTION_BLOCKING 높이 + 바이옴 강수 종류 RAIN. */
class RainingAtTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    static BlockPlacementTest.TestLevel level(boolean raining, String biome) {
        return new BlockPlacementTest.TestLevel() {
            @Override protected LevelServices services() {
                return new LevelServices.Delegating(LevelServices.DEFAULT) {
                    @Override public boolean isRaining() { return raining; }
                    @Override public String biomeAt(int x, int y, int z) { return biome; }
                };
            }
        };
    }

    private static Pos surface(BlockPlacementTest.TestLevel l) { return new Pos(3, l.getMotionBlockingHeight(3, 3), 3); }

    @Test
    void rain_openSky_plains() {
        var l = level(true, "minecraft:plains");
        Pos top = surface(l);
        assertTrue(l.isRainingAt(top));
        assertTrue(l.isRainingAt(top.above(10)));
        assertFalse(l.isRainingAt(top.below()), "높이 지도 칸 아래는 가려져 있다");
    }

    @Test
    void notRaining_false() {
        var l = level(false, "minecraft:plains");
        assertFalse(l.isRainingAt(surface(l)));
    }

    @Test
    void roofBlocksRain() {
        var l = level(true, "minecraft:plains");
        Pos top = surface(l);
        l.setBlock(top.above(3), Blocks.get().defaultState("minecraft:stone"), 2);
        assertFalse(l.isRainingAt(top));
        assertTrue(l.isRainingAt(top.above(4)));
    }

    @Test
    void biomePrecipitation() {
        var desert = level(true, "minecraft:desert");
        assertFalse(desert.isRainingAt(surface(desert)), "사막은 강수 없음");
        var snowy = level(true, "minecraft:snowy_plains");
        assertFalse(snowy.isRainingAt(surface(snowy)), "눈 바이옴은 비가 아니라 눈");
    }
}
