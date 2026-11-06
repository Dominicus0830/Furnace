// BoatItem.use: 시선이 물(유체 포함)에 맞으면 그 지점에 종류별 배를 플레이어 방향으로 놓고 하나 쓴다. 빗나가면 넘긴다.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.entity.Boat;
import org.domi.furnace.server.entity.ChestBoat;
import org.domi.furnace.server.entity.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BoatItemTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack item(String n, int c) { return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", n), c); }

    @Test
    void placesBoatOnWaterAndConsumes() {
        var level = new BlockPlacementTest.TestLevel();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:water"), 3);
        var a = new BlockPlacementTest.Actor();
        a.xRot = 90.0F;
        a.yRot = 45.0F;
        a.hands[0] = item("spruce_boat", 2);
        var r = ItemUse.useItem(level, a, 0);
        assertTrue(r.consumesAction(), "놓았다: " + r);
        assertEquals(1, a.hands[0].count());
        Boat boat = null;
        for (var e : level.entities().all()) if (e instanceof Boat b) boat = b;
        assertNotNull(boat);
        assertFalse(boat instanceof ChestBoat);
        assertEquals(Boat.Type.SPRUCE, boat.getVariant());
        assertEquals(45.0F, boat.yRot());
        assertEquals(-61 + 0.8888889F, boat.y(), 1e-5, "물 표면(유체도 맞춘다)");
        var level2 = new BlockPlacementTest.TestLevel();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) level2.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:water"), 3);
        a.hands[0] = item("bamboo_chest_raft", 1);
        ItemUse.useItem(level2, a, 0);
        int chest = 0;
        for (var e : level2.entities().all()) if (e instanceof ChestBoat cb && cb.getVariant() == Boat.Type.BAMBOO) chest++;
        assertEquals(1, chest);
    }

    @Test
    void missPasses() {
        var level = new BlockPlacementTest.TestLevel();
        var a = new BlockPlacementTest.Actor();
        a.xRot = -90.0F;   // 하늘
        a.hands[0] = item("oak_boat", 1);
        var r = ItemUse.useItem(level, a, 0);
        assertFalse(r.consumesAction());
        assertEquals(1, a.hands[0].count());
    }
}
