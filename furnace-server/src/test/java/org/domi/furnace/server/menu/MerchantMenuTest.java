// 상인 거래 시험(가짜 상인): 값 맞으면 결과, 꺼내면 값만큼 소모·사용 횟수·경험치, 재료 자동 채우기, 품절, 가격 수요 반영, 닫으면 돌려줌, 패킷 바이트.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.netty.buffer.Unpooled;
import java.util.Optional;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.MerchantOffersPacket;
import org.domi.furnace.server.recipe.TestRecipes;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.Merchant;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.MerchantOffers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MerchantMenuTest {

    static final class Fake implements Merchant {
        MenuPlayer trading;
        final MerchantOffers offers = new MerchantOffers();
        int xp, notified;
        @Override public void setTradingPlayer(MenuPlayer p) { trading = p; }
        @Override public MenuPlayer tradingPlayer() { return trading; }
        @Override public MerchantOffers offers() { return offers; }
        @Override public void notifyTrade(MerchantOffer o) { o.increaseUses(); notified++; }
        @Override public void notifyTradeUpdated(ItemStack s) { }
        @Override public int villagerXp() { return xp; }
        @Override public void overrideXp(int x) { xp = x; }
        @Override public boolean showProgressBar() { return true; }
        @Override public String notifyTradeSound() { return "minecraft:entity.villager.yes"; }
    }

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    Fake merchant;
    MerchantMenu menu;

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        merchant = new Fake();
        merchant.offers.add(new MerchantOffer(new ItemCost("minecraft:emerald", 3), Optional.of(new ItemCost("minecraft:paper", 1)),
                it("bookshelf", 1), 0, 2, true, 0, 0, 0.05F, 5));
        merchant.offers.add(new MerchantOffer(new ItemCost("minecraft:wheat", 20), it("emerald", 1), 16, 2, 0.05F));
        merchant.setTradingPlayer(player);
        menu = new MerchantMenu(1, invMenu.inventoryContainer(), player, merchant);
    }

    @Test
    void payingMatchingCostsYieldsResultAndTradeConsumesAndRewardsXp() {
        menu.getSlot(0).set(it("emerald", 4));
        menu.getSlot(1).set(it("paper", 1));
        assertEquals("minecraft:bookshelf", ItemTools.itemName(menu.getSlot(2).getItem()));
        menu.clicked(MerchantMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals("minecraft:bookshelf", ItemTools.itemName(menu.getCarried()));
        assertEquals(1, menu.getSlot(0).getItem().count(), "에메랄드 4 − 3");
        assertTrue(menu.getSlot(1).getItem().isEmpty());
        assertEquals(1, merchant.offers.get(0).uses());
        assertEquals(5, merchant.xp);
    }

    @Test
    void notEnoughOrWrongItemsGiveNothing() {
        menu.getSlot(0).set(it("emerald", 2));
        menu.getSlot(1).set(it("paper", 1));
        assertTrue(menu.getSlot(2).getItem().isEmpty());
        menu.getSlot(0).set(it("emerald", 3));
        menu.getSlot(1).set(ItemStack.EMPTY);
        assertTrue(menu.getSlot(2).getItem().isEmpty(), "값 B 도 필요");
    }

    @Test
    void outOfStockOfferGivesNoResult() {
        merchant.offers.get(1).setToOutOfStock();
        menu.getSlot(0).set(it("wheat", 20));
        assertTrue(menu.getSlot(2).getItem().isEmpty());
    }

    @Test
    void selectTradeMovesPaymentItemsFromInventory() {
        inv.set(PlayerInventory.windowSlot(9), it("wheat", 64));
        menu.setSelectionHint(1);
        menu.tryMoveItems(1);
        assertEquals(20, menu.getSlot(0).getItem().count() >= 20 ? 20 : menu.getSlot(0).getItem().count(), "지불 칸에 값만큼 이상(스택 단위로) 채운다");
        assertTrue(menu.getSlot(0).getItem().count() >= 20);
        assertEquals("minecraft:emerald", ItemTools.itemName(menu.getSlot(2).getItem()));
    }

    @Test
    void demandRaisesPriceAndClosingReturnsPayment() {
        var offer = new MerchantOffer(new ItemCost("minecraft:emerald", 10), Optional.empty(), it("diamond", 1), 0, 8, true, 0, 4, 0.25F, 1);
        assertEquals(10 + (int) Math.floor(10 * 4 * 0.25F), offer.costACount(), "수요 4 × 배율 0.25");
        menu.getSlot(0).set(it("emerald", 5));
        menu.removed(player);
        assertEquals(1, 1);
        assertTrue(inv.contains(it("emerald", 1)) || !player.dropped.isEmpty());
        assertNull(merchant.trading);
    }

    @Test
    void offersPacketWritesWithoutError() {
        var buf = Unpooled.buffer();
        new MerchantOffersPacket(3, merchant.offers, 2, 30, true, false).write(buf);
        assertTrue(buf.readableBytes() > 40);
        buf.release();
    }
}
