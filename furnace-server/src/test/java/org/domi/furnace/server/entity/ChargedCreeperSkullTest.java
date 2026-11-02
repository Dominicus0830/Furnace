// 충전 크리퍼 폭발에 죽은 몹의 머리 드롭: 크리퍼당 한 번, 종류별 아이템, 충전 안 된 크리퍼는 없음.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChargedCreeperSkullTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final Entity.Damage BOOM = new Entity.Damage("explosion", false, true, false);

    private static Creeper creeper(ZombieCombatTest.ArenaLevel level, boolean powered) {
        var c = (Creeper) Entities.create(level, "minecraft:creeper");
        c.moveTo(0.5, -60, 5.5, 0, 0);
        c.entityData.set(Creeper.DATA_IS_POWERED, powered);
        level.addFreshEntity(c);
        return c;
    }

    private static int skulls(ZombieCombatTest.ArenaLevel level, String item) {
        int n = 0;
        for (var e : level.getEntities(null, new AABB(-4, -62, -4, 8, -57, 12), x -> x instanceof ItemEntity)) {
            if (ItemTools.itemName(((ItemEntity) e).getItem()).equals(item)) n += ((ItemEntity) e).getItem().count();
        }
        return n;
    }

    private static void kill(ZombieCombatTest.ArenaLevel level, String type, Creeper by) {
        level.dayTime = 18000;
        var m = (LivingEntity) Entities.create(level, type);
        m.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(m);
        m.hurtByVia(BOOM, 1000.0F, by, by);
        assertTrue(m.isDeadOrDying(), type);
    }

    @Test
    void chargedCreeperSkullOncePerCreeper() {
        var level = new ZombieCombatTest.ArenaLevel();
        var c = creeper(level, true);
        kill(level, "minecraft:zombie", c);
        assertEquals(1, skulls(level, "minecraft:zombie_head"));
        kill(level, "minecraft:zombie", c);   // 같은 크리퍼의 두 번째 희생자: 머리 없음
        assertEquals(1, skulls(level, "minecraft:zombie_head"));
        assertFalse(c.canDropMobsSkull());
    }

    @Test
    void headItemPerType() {
        String[][] cases = {
            {"minecraft:husk", "minecraft:zombie_head"}, {"minecraft:drowned", "minecraft:zombie_head"},
            {"minecraft:skeleton", "minecraft:skeleton_skull"}, {"minecraft:wither_skeleton", "minecraft:wither_skeleton_skull"},
            {"minecraft:piglin", "minecraft:piglin_head"}, {"minecraft:creeper", "minecraft:creeper_head"},
        };
        for (String[] cs : cases) {
            var level = new ZombieCombatTest.ArenaLevel();
            kill(level, cs[0], creeper(level, true));
            assertEquals(1, skulls(level, cs[1]), cs[0]);
        }
    }

    @Test
    void unchargedCreeperDropsNothing() {
        var level = new ZombieCombatTest.ArenaLevel();
        kill(level, "minecraft:zombie", creeper(level, false));
        assertEquals(0, skulls(level, "minecraft:zombie_head"));
    }
}
