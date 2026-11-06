// 당근 낚싯대 사용(FoodOnAStickItem.use): 내구도 7 소모, 닳으면 낚싯대로 교체, 이미 가속 중이면 PASS.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.block.ItemUse;
import org.domi.furnace.server.block.PlayerActor;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PigCarrotStickTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static ItemStack stick(int damage) {
        return ItemTools.withDamage(new ItemStack(ItemContext.id("minecraft:item", "carrot_on_a_stick"), 1), damage);
    }

    private record Setup(BlockPlacementTest.TestLevel level, Pig pig, Player p) {}

    private static Setup ride() {
        var level = new BlockPlacementTest.TestLevel();
        Pig pig = new Pig(level, level.nextEntityId());
        pig.moveTo(0.5, -60, 0.5, 0, 0);
        pig.setSaddled(true);
        Player p = new Player(952, UUID.randomUUID(), "rider", new ConcurrentEmbeddedChannel());
        p.vitals().env(new PlayerVitalsTest.TestEnv());
        p.teleporter(t -> {});
        p.applyMove(2.5, -60, 0.5, 0, 0, true);
        assertTrue(p.startRiding(pig, true));
        return new Setup(level, pig, p);
    }

    @Test
    void useBoostsAndDamagesSeven() {
        var s = ride();
        var actor = new PlayerActor(s.p, stick(0), ItemStack.EMPTY, 0);
        ItemUse.useItem(s.level, actor, 0);
        assertEquals(7, ItemTools.damage(actor.itemInHand(0)));
        // 이미 가속 중: boost 거짓 → 내구도 그대로
        ItemUse.useItem(s.level, actor, 0);
        assertEquals(7, ItemTools.damage(actor.itemInHand(0)));
    }

    @Test
    void brokenStickBecomesFishingRod() {
        var s = ride();
        var actor = new PlayerActor(s.p, stick(20), ItemStack.EMPTY, 0);
        ItemUse.useItem(s.level, actor, 0);
        assertEquals("minecraft:fishing_rod", ItemTools.itemName(actor.itemInHand(0)));
        assertEquals(1, actor.itemInHand(0).count());
    }
}
