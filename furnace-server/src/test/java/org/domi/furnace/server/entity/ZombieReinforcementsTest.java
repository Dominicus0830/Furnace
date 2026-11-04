// 좀비 지원군: 어려움에서 맞으면 7~40칸 밖에 새 좀비가 나고 부른 쪽 확률이 0.05 줄어든다. 보통 난이도·확률 0 이면 안 난다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.GameMode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ZombieReinforcementsTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static final class Dark extends BlockPlacementTest.TestLevel {
        int difficulty = 3;
        Player player;

        @Override public int getSkyLight(Pos pos) { return 0; }
        @Override public int getBlockLight(Pos pos) { return 0; }
        @Override public int getRawBrightness(Pos pos, int skyDarken) { return 0; }

        @Override
        protected LevelServices services() {
            return new LevelServices.Delegating(LevelServices.DEFAULT) {
                @Override public int difficulty() { return difficulty; }
                @Override public long dayTime() { return 18000; }
                @Override public void forEachPlayer(java.util.function.Consumer<Player> c) { if (player != null) c.accept(player); }
            };
        }
    }

    private static Dark arena() {
        var level = new Dark();
        for (int x = -32; x < 48; x++) for (int z = -32; z < 48; z++) level.setBlock(new Pos(x, -61, z), B.defaultState("minecraft:grass_block"), 3);
        var p = new Player(9, java.util.UUID.randomUUID(), "Alex", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.vitals().loadGameTypes(null, GameMode.SURVIVAL, false);
        p.applyMove(8.5, -60, 8.5, 0, 0, true);
        level.player = p;
        return level;
    }

    private static final Entity.Damage HIT = new Entity.Damage("mob_attack", false, false, false);

    private static Zombie zombie(Dark level, double chance) {
        var z = new Zombie(level, level.nextEntityId());
        z.moveTo(8.5, -60, 8.5, 0, 0);
        level.addFreshEntity(z);
        z.attributes().get(Attribute.SPAWN_REINFORCEMENTS).setBaseValue(chance);
        return z;
    }

    @Test
    void hurtOnHardCallsReinforcement() {
        var level = arena();
        var z = zombie(level, 1.0);
        assertTrue(z.hurtBy(HIT, 1.0F, level.player));
        var zombies = level.entities().tickSnapshot().stream().filter(e -> e instanceof Zombie && e != z).toList();
        assertEquals(1, zombies.size(), "지원군 한 마리");
        Entity c = zombies.get(0);
        double d2 = Math.pow(c.x() - z.x(), 2) + Math.pow(c.y() - z.y(), 2) + Math.pow(c.z() - z.z(), 2);
        assertTrue(d2 >= 49.0, "7칸 이상 떨어져 난다: " + d2);
        assertEquals(1.0 - 0.05, z.attributeValue(Attribute.SPAWN_REINFORCEMENTS), 1.0E-9, "부른 쪽 -0.05");
        var callee = ((Zombie) c).attributes().get(Attribute.SPAWN_REINFORCEMENTS).getModifier("minecraft:reinforcement_callee_charge");
        assertNotNull(callee, "불려온 쪽은 영구 보정을 받는다");
        assertEquals(-0.05, callee.amount(), 1.0E-6);
    }

    @Test
    void noReinforcementOnNormalOrZeroChance() {
        var normal = arena();
        normal.difficulty = 2;
        var z1 = zombie(normal, 1.0);
        z1.hurtBy(HIT, 1.0F, normal.player);
        assertEquals(1L, normal.entities().tickSnapshot().stream().filter(e -> e instanceof Zombie).count());

        var zero = arena();
        var z2 = zombie(zero, 0.0);
        z2.hurtBy(HIT, 1.0F, zero.player);
        assertEquals(1L, zero.entities().tickSnapshot().stream().filter(e -> e instanceof Zombie).count());
    }
}
