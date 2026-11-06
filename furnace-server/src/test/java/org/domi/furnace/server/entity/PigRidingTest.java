// 돼지 타기: 안장 있어야 탄다, 당근 낚싯대를 들면 조종, 가속(ItemBasedSteering) 총 시간 난수와 종료 시점.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PigRidingTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static Pig pig(BlockPlacementTest.TestLevel level) {
        Pig p = new Pig(level, level.nextEntityId());
        p.moveTo(0.5, -60, 0.5, 0, 0);
        p.setOnGround(true);
        return p;
    }

    private static Player player() {
        Player p = new Player(951, UUID.randomUUID(), "rider", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 30.0F, 20.0F, true);
        return p;
    }

    private static ItemStack item(String name) {
        return new ItemStack(org.domi.furnace.server.item.ItemContext.id("minecraft:item", name), 1);
    }

    @Test
    void ridesOnlyWithSaddleAndControlsOnlyWithCarrotStick() {
        var level = new BlockPlacementTest.TestLevel();
        Pig pig = pig(level);
        Player p = player();
        pig.interact(p, 0, ItemStack.EMPTY, null);
        assertNull(p.vehicle(), "안장이 없으면 타지 못한다");

        pig.setSaddled(true);
        assertTrue(pig.interact(p, 0, ItemStack.EMPTY, null));
        assertSame(pig, p.vehicle());
        assertNull(pig.getControllingPassenger(), "당근 낚싯대가 없으면 조종하지 못한다");

        var inv = p.inventory();
        inv.set(inv.heldWindowSlot(), item("carrot_on_a_stick"));
        assertSame(p, pig.getControllingPassenger());
        inv.set(inv.heldWindowSlot(), item("stick"));
        assertNull(pig.getControllingPassenger());
    }

    @Test
    void boostTotalTimeAndEndFollowVanilla() throws Exception {
        var level = new BlockPlacementTest.TestLevel();
        Pig pig = pig(level);
        pig.setSaddled(true);
        var f = Entity.class.getDeclaredField("random");
        f.setAccessible(true);
        f.set(pig, new LegacyRandomSource(77L));
        int total = new LegacyRandomSource(77L).nextInt(841) + 140;
        Player p = player();
        pig.interact(p, 0, ItemStack.EMPTY, null);

        assertTrue(pig.boost());
        assertEquals(total, (Integer) pig.entityData.get(Pig.DATA_BOOST_TIME));
        assertFalse(pig.boost(), "가속 중에는 다시 못 한다");
        // tickBoost: (boostTime++ > total) — total+1 번째 호출까지 가속, total+2 번째에 끝
        for (int i = 0; i < total + 1; i++) pig.tickRidden(p);
        assertFalse(pig.boost());
        pig.tickRidden(p);
        assertTrue(pig.boost());
        assertEquals(p.yaw(), pig.yRot());
        assertEquals(p.pitch() * 0.5F, pig.xRot());
    }
}
