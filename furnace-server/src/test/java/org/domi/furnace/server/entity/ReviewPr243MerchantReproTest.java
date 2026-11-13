// PR #243 검토 재현: 상인(주민)이 거래 상대를 놓지 않은 채 지워지면(플러그인 remove·변환·청크 내림) 거래 창이 유효하게 남는다.
// Paper 는 ServerLevel 엔티티 제거 훅이 merchant.getTrader() 의 창을 UNLOADED 로 닫는다("Fix merchant inventory not closing on entity removal").
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.api.event.inventory.InventoryCloseEvent.Reason;
import org.domi.furnace.server.block.BlockPlacementTest;
import org.domi.furnace.server.menu.Container;
import org.domi.furnace.server.menu.MerchantMenu;
import org.domi.furnace.server.menu.PlayerInventoryContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewPr243MerchantReproTest {
    BlockPlacementTest.TestLevel level;
    PlayerInventory inv;
    EntityInventoryMenuTest.FakePlayer mp;
    Container invC;

    @BeforeEach
    void setUp() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        level = new BlockPlacementTest.TestLevel();
        inv = new PlayerInventory();
        mp = new EntityInventoryMenuTest.FakePlayer(inv);
        invC = new PlayerInventoryContainer(inv);
    }

    private Villager trading() {
        var v = new Villager(level, level.nextEntityId());
        v.moveTo(0.5, -60, 0.5, 0, 0);
        v.setProfession("farmer");
        v.setTradingPlayer(mp);
        return v;
    }

    @Test
    void villagerDiscardedWhileTradingClosesTheWindow() {
        var v = trading();
        var m = new MerchantMenu(1, invC, mp, v);
        assertTrue(m.stillValid(mp));
        v.setRemoved(Entity.RemovalReason.DISCARDED);
        assertFalse(m.stillValid(mp), "지워진 상인의 창은 더는 유효하지 않아야 한다");
        assertEquals(Reason.UNLOADED, m.invalidCloseReason());
    }

    @Test
    void villagerUnloadedWithChunkWhileTradingClosesTheWindow() {
        var v = trading();
        var m = new MerchantMenu(1, invC, mp, v);
        v.setRemoved(Entity.RemovalReason.UNLOADED_TO_CHUNK);
        assertFalse(m.stillValid(mp));
    }

    @Test
    void wanderingTraderDiscardedWhileTradingClosesTheWindow() {
        var w = new WanderingTrader(level, level.nextEntityId());
        w.moveTo(0.5, -60, 0.5, 0, 0);
        w.setTradingPlayer(mp);
        var m = new MerchantMenu(1, invC, mp, w);
        w.setRemoved(Entity.RemovalReason.DISCARDED);
        assertFalse(m.stillValid(mp));
    }

    @Test
    void controlTradingPlayerReleasedIsCantUse() {   // 죽음 등으로 거래 상대를 놓은 경우(Paper 도 틱 검사로 CANT_USE) — 통과해야 한다
        var v = trading();
        var m = new MerchantMenu(1, invC, mp, v);
        v.setTradingPlayer(null);
        assertFalse(m.stillValid(mp));
        assertEquals(Reason.CANT_USE, m.invalidCloseReason());
    }
}
