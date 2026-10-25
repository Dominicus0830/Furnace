// 윈드 차지: 블록에 맞으면 피해 없이 문을 열고, 몹을 밀어 올린다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.Pos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WindChargeTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void burstOpensWoodenTrapdoorAndPushesWithoutDamage() {
        var level = new BlockPlacementTest.TestLevel();
        int closed = B.defaultState(B.id("minecraft:oak_trapdoor"));
        Pos pos = new Pos(5, -60, 5);
        level.setBlock(pos, closed, 3);
        Cow cow = new Cow(level, level.nextEntityId());
        cow.moveTo(5.5, -60.0, 6.5, 0, 0);
        level.addFreshEntity(cow);
        float hp = cow.health();
        WindCharge charge = new WindCharge(level, level.nextEntityId());
        charge.moveTo(5.5, -59.0, 5.5, 0, 0);
        level.addFreshEntity(charge);
        charge.explodeAt(5.5, -59.5, 5.5);
        assertNotEquals(closed, level.getBlockState(pos), "트랩도어가 열렸다");
        assertEquals(hp, cow.health(), 1.0E-6F, "피해 없음");
        assertTrue(cow.vy() > 0.0 || cow.vx() != 0.0 || cow.vz() != 0.0, "밀렸다");
    }
}
