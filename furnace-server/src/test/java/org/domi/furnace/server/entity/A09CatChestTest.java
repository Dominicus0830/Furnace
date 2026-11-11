// A09-04: 상자 위에 앉은 고양이가 있으면 상자가 막힌다(바닐라 ChestBlock.isChestBlockedAt = 전도체 || isCatSittingOnChest).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.ChestBlock;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class A09CatChestTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Cat cat(PlayerSleepingTest.Lvl level, double x, double y, double z, boolean sit) {
        var c = new Cat(level, level.nextEntityId());
        c.moveTo(x, y, z, 0, 0);
        level.addFreshEntity(c);
        c.setTame(true, false);
        c.setInSittingPose(sit);
        return c;
    }

    @Test
    void sittingCatAboveBlocksChest() {
        var level = new PlayerSleepingTest.Lvl();
        Pos chest = new Pos(2, -60, 2);
        level.setBlock(chest, B.defaultState("minecraft:chest"), 3);
        assertFalse(ChestBlock.isChestBlockedAt(level, chest));
        cat(level, 2.5, -59, 2.5, true);
        assertTrue(ChestBlock.isChestBlockedAt(level, chest));
    }

    @Test
    void standingCatOrOtherPositionDoesNotBlock() {
        var level = new PlayerSleepingTest.Lvl();
        Pos chest = new Pos(2, -60, 2);
        level.setBlock(chest, B.defaultState("minecraft:chest"), 3);
        cat(level, 2.5, -59, 2.5, false);
        cat(level, 6.5, -59, 6.5, true);
        assertFalse(ChestBlock.isChestBlockedAt(level, chest));
    }
}
