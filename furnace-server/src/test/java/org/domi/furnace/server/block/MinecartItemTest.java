// MinecartItem.useOn: 레일에만 놓는다(칸 가운데, 바닥 + 0.0625, 경사면 + 0.5). 레일이 아니면 실패.
package org.domi.furnace.server.block;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.entity.AbstractMinecart;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.MinecartChest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MinecartItemTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack item(String n, int c) { return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", n), c); }

    @Test
    void placesOnRailOnly() {
        var level = new BlockPlacementTest.TestLevel();
        level.setBlock(new Pos(0, -61, 0), B.defaultState("minecraft:stone"), 3);
        level.setBlock(new Pos(3, -61, 0), B.defaultState("minecraft:stone"), 3);
        level.setBlock(new Pos(0, -60, 0), B.defaultState("minecraft:rail"), 3);
        var a = new BlockPlacementTest.Actor();
        a.hands[0] = item("chest_minecart", 2);
        var r = ItemUse.useOn(level, a, 0, new Hit(new Pos(0, -60, 0), Dir.UP, 0.5, -59.9, 0.5, false));
        assertTrue(r.consumesAction(), "놓았다: " + r);
        assertEquals(1, a.hands[0].count());
        AbstractMinecart cart = null;
        for (var e : level.entities().all()) if (e instanceof AbstractMinecart m) cart = m;
        assertInstanceOf(MinecartChest.class, cart);
        assertEquals(0.5, cart.x());
        assertEquals(-60 + 0.0625, cart.y(), 1e-12);
        assertEquals(0.5, cart.z());
        var r2 = ItemUse.useOn(level, a, 0, new Hit(new Pos(3, -61, 0), Dir.UP, 3.5, -60, 0.5, false));
        assertFalse(r2.consumesAction(), "레일이 아니다");
        assertEquals(1, a.hands[0].count());
    }
}
