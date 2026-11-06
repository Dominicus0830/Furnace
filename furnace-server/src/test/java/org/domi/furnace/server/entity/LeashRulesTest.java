// canBeLeashed 재정의(디컴파일 y()): 박쥐·판다·거북·물 동물(돌고래·오징어 제외)·주민·방랑 상인은 불가, Hoglin/Zoglin 은 Enemy 여도 가능.
// 끈 아이템을 든 주민 우클릭은 끈 맺기가 아니라 거래로 가야 한다(interactLeash 가 거절).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.util.UUID;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.item.ItemContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LeashRulesTest {

    @BeforeAll
    static void install() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    @Test
    void canBeLeashedTable() {
        var l = new BlockPlacementTest.TestLevel();
        assertFalse(new Bat(l, l.nextEntityId()).canBeLeashed());
        assertFalse(new Panda(l, l.nextEntityId()).canBeLeashed());
        assertFalse(new Turtle(l, l.nextEntityId()).canBeLeashed());
        assertFalse(new Cod(l, l.nextEntityId()).canBeLeashed());
        assertTrue(new Squid(l, l.nextEntityId()).canBeLeashed());
        assertTrue(new Dolphin(l, l.nextEntityId()).canBeLeashed());
        assertFalse(new Villager(l, l.nextEntityId()).canBeLeashed());
        assertFalse(new WanderingTrader(l, l.nextEntityId()).canBeLeashed());
        assertTrue(new Hoglin(l, l.nextEntityId()).canBeLeashed());
        assertTrue(new Zoglin(l, l.nextEntityId()).canBeLeashed());
        assertFalse(new Zombie(l, l.nextEntityId()).canBeLeashed());
        assertTrue(new Cow(l, l.nextEntityId()).canBeLeashed());
        assertTrue(new Wolf(l, l.nextEntityId()).canBeLeashed());
    }

    private static Player playerWithLead(int count) {
        Player p = new Player(1, UUID.randomUUID(), "A", new ConcurrentEmbeddedChannel());
        var inv = p.inventory();
        inv.set(inv.heldWindowSlot(), new ItemStack(ItemContext.id("minecraft:item", "lead"), count));
        return p;
    }

    @Test
    void villagerWithLeadIsNotLeashedSoTradeProceeds() {
        var l = new BlockPlacementTest.TestLevel();
        Player p = playerWithLead(3);
        Villager v = new Villager(l, l.nextEntityId());
        l.addFreshEntity(v);
        var held = p.inventory().get(p.inventory().heldWindowSlot());
        assertFalse(v.interactLeash(p, 0, held));
        assertFalse(v.isLeashed());
        assertEquals(3, p.inventory().get(p.inventory().heldWindowSlot()).count());
    }

    @Test
    void cowWithLeadIsLeashedAndLeadConsumed() {
        var l = new BlockPlacementTest.TestLevel();
        Player p = playerWithLead(3);
        Cow c = new Cow(l, l.nextEntityId());
        l.addFreshEntity(c);
        var held = p.inventory().get(p.inventory().heldWindowSlot());
        assertTrue(c.interactLeash(p, 0, held));
        assertSame(p, c.getLeashHolder());
        assertEquals(2, p.inventory().get(p.inventory().heldWindowSlot()).count());
    }

    @Test
    void removedHolderInThisRegionDropsLeadButForeignHolderIsNotRead() {
        var l = new BlockPlacementTest.TestLevel();
        var other = new BlockPlacementTest.TestLevel();   // 다른 region 에 해당
        var k = new LeashFenceKnotEntity(l, l.nextEntityId(), new org.domi.furnace.server.block.Pos(2, -60, 0));
        l.addFreshEntity(k);
        Cow c = new Cow(l, l.nextEntityId());
        c.moveTo(0.5, -59.625, 0.5, 0, 0);
        l.addFreshEntity(c);
        c.setLeashedTo(k, false);
        c.baseTick();
        assertTrue(c.isLeashed());
        k.discard();
        c.baseTick();
        assertFalse(c.isLeashed());
        // 처음부터 다른 레벨 소속인 붙잡은 쪽: 읽지 않고 그대로 둔다
        var fk = new LeashFenceKnotEntity(other, other.nextEntityId(), new org.domi.furnace.server.block.Pos(2, -60, 0));
        other.addFreshEntity(fk);
        Cow d = new Cow(l, l.nextEntityId());
        d.moveTo(0.5, -59.625, 0.5, 0, 0);
        l.addFreshEntity(d);
        d.setLeashedTo(fk, false);
        d.baseTick();
        assertTrue(d.isLeashed());
    }
}
