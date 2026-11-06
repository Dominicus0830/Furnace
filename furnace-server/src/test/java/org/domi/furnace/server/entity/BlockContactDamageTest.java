// 닿은 블록이 생물에게 주는 효과: 선인장 피해 1, 마그마 블록 hot_floor 1, 위더 장미 시듦.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Blocks;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.effect.MobEffect;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BlockContactDamageTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Chicken chicken(SkeletonCombatTest.ArenaLevel level) {
        var c = new Chicken(level, level.nextEntityId());
        c.moveTo(0.5, -60, 0.5, 0, 0);
        level.addFreshEntity(c);
        return c;
    }

    /** Spider.makeStuckInBlock(cko:74): 거미줄이면 무시(낙하 거리도 그대로). 닭은 걸린다. */
    @Test
    void spidersIgnoreWeb() {
        var level = new SkeletonCombatTest.ArenaLevel();
        int s = Blocks.get().defaultState("minecraft:cobweb");
        for (String type : new String[] {"minecraft:spider", "minecraft:cave_spider", "minecraft:chicken"}) {
            var m = (Entity) Entities.create(level, type);
            m.moveTo(0.5, -60, 0.5, 0, 0);
            level.addFreshEntity(m);
            m.fallDistance = 5.0F;
            B.behavior(s).entityInside(s, level, new Pos(0, -60, 0), m);
            assertEquals(type.endsWith("chicken") ? 0.0F : 5.0F, m.fallDistance, type);
        }
    }

    @Test
    void cactusHurtsOne() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var c = chicken(level);
        float before = c.health();
        int s = Blocks.get().defaultState("minecraft:cactus");
        B.behavior(s).entityInside(s, level, new Pos(0, -60, 0), c);
        assertEquals(before - 1.0F, c.health());
    }

    @Test
    void magmaHurtsOneUnlessSteppingCarefully() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var c = chicken(level);
        float before = c.health();
        int s = Blocks.get().defaultState("minecraft:magma_block");
        B.behavior(s).stepOn(level, new Pos(0, -61, 0), s, c);
        assertEquals(before - 1.0F, c.health());
    }

    @Test
    void witherRoseWithersLivingMob() {
        var level = new SkeletonCombatTest.ArenaLevel();
        var c = chicken(level);
        int s = Blocks.get().defaultState("minecraft:wither_rose");
        B.behavior(s).entityInside(s, level, new Pos(0, -60, 0), c);
        assertTrue(c.hasEffect(MobEffect.WITHER));
    }
}
