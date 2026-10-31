// 폭죽 소유자: 주민이 쏜 축하 폭죽은 소유자(주민)에게 맞지 않아 몸통에서 터지지 않고, 소유자 없는 폭죽은 첫 틱에 맞고 터진다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.entity.ai.behavior.CelebrateVillagersSurvivedRaid;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FireworkOwnerTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Villager villager(BlockPlacementTest.TestLevel level) {
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(v);
        return v;
    }

    @Test
    void ownedRocketDoesNotHitItsOwner() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level);
        float hp = v.health();
        var rocket = new FireworkRocketEntity(level, level.nextEntityId(), v, v.x(), v.eyeY(), v.z(), CelebrateVillagersSurvivedRaid.getFirework(DyeColor.RED, 0));
        level.addFreshEntity(rocket);
        for (int i = 0; i < 3; i++) rocket.tick();
        assertFalse(rocket.isRemoved(), "소유자 몸통에서 터지지 않는다");
        assertEquals(hp, v.health(), "주민은 피해를 받지 않았다");
    }

    @Test
    void unownedRocketExplodesInsideVillager() {
        var level = new BlockPlacementTest.TestLevel();
        var v = villager(level);
        float hp = v.health();
        var rocket = new FireworkRocketEntity(level, level.nextEntityId(), v.x(), v.eyeY(), v.z(), CelebrateVillagersSurvivedRaid.getFirework(DyeColor.RED, 0));
        level.addFreshEntity(rocket);
        rocket.tick();
        assertTrue(rocket.isRemoved(), "소유자가 없으면 첫 틱에 주민에 맞고 터진다");
        assertTrue(v.health() < hp, "주민이 맞았다");
    }
}
