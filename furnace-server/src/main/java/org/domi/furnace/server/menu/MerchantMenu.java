// 바닐라 MerchantMenu(상인 거래, merchant) — 칸: 0 값 A, 1 값 B, 2 결과, 3..29 주 인벤토리, 30..38 핫바. 모든 칸은 shift/더블클릭 모으기에서 제외.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.trade.ItemCost;
import org.domi.furnace.server.trade.Merchant;
import org.domi.furnace.server.trade.MerchantOffer;

public class MerchantMenu extends AbstractContainerMenu {

    public static final int PAYMENT1_SLOT = 0, PAYMENT2_SLOT = 1, RESULT_SLOT = 2, INV_SLOT_START = 3, INV_SLOT_END = 30, USE_ROW_START = 30, USE_ROW_END = 39;

    private final Merchant trader;
    private final TradeContainer tradeContainer;
    private final MenuPlayer owner;

    public MerchantMenu(int containerId, Container playerInventory, MenuPlayer owner, Merchant trader) {
        super("minecraft:merchant", containerId);
        this.trader = trader;
        this.owner = owner;
        this.tradeContainer = new TradeContainer(trader);
        addSlot(new Slot(tradeContainer, 0));
        addSlot(new Slot(tradeContainer, 1));
        addSlot(new MerchantResultSlot(owner, trader, tradeContainer, 2));
        for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c + r * 9 + 9));
        for (int c = 0; c < 9; c++) addSlot(new Slot(playerInventory, c));
    }

    public TradeContainer tradeContainer() { return tradeContainer; }

    @Override public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) { return false; }

    @Override public boolean stillValid(MenuPlayer player) { return trader.tradingPlayer() == player; }

    @Override public org.domi.furnace.api.event.inventory.InventoryCloseEvent.Reason invalidCloseReason() {
        return trader instanceof org.domi.furnace.server.entity.Entity e ? reasonForEntity(e) : super.invalidCloseReason();
    }

    @Override
    public void slotsChanged(Container container) {
        tradeContainer.updateSellItem();
        super.slotsChanged(container);
    }

    public void setSelectionHint(int hint) { tradeContainer.setSelectionHint(hint); }

    @Override
    public ItemStack quickMoveStack(MenuPlayer player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) return result;
        ItemStack stack = slot.getItem();
        result = stack;
        Moved moved;
        if (index == RESULT_SLOT) {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_END, true);
            if (!moved.changed()) return ItemStack.EMPTY;
            slot.onQuickCraft(moved.remaining(), result);
            playTradeSound();
        } else if (index != PAYMENT1_SLOT && index != PAYMENT2_SLOT) {
            if (index >= INV_SLOT_START && index < INV_SLOT_END) {
                moved = moveItemStackTo(stack, USE_ROW_START, USE_ROW_END, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else if (index >= USE_ROW_START && index < USE_ROW_END) {
                moved = moveItemStackTo(stack, INV_SLOT_START, INV_SLOT_END, false);
                if (!moved.changed()) return ItemStack.EMPTY;
            } else {
                return result;   // 바닐라: 어느 쪽에도 안 속하면 이동 없이 (실제로는 도달하지 않는다)
            }
        } else {
            moved = moveItemStackTo(stack, INV_SLOT_START, USE_ROW_END, false);
            if (!moved.changed()) return ItemStack.EMPTY;
        }
        stack = moved.remaining();
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setCountInPlace(stack);
        slot.setChanged();
        if (stack.count() == result.count()) return ItemStack.EMPTY;
        slot.onTake(player, stack);
        return result;
    }

    /** 서버 쪽 소리는 상인이 낸다(주민 위치에서 NEUTRAL) — TODO(엔티티): 주민이 생기면. */
    private void playTradeSound() { }

    @Override
    public void removed(MenuPlayer player) {
        super.removed(player);
        trader.setTradingPlayer(null);
        boolean gone = player.isDeadOrDying() || player.hasDisconnected();
        ItemStack a = tradeContainer.removeNoUpdate(0), b = tradeContainer.removeNoUpdate(1);
        for (ItemStack s : new ItemStack[]{a, b}) {
            if (s.isEmpty()) continue;
            if (gone) player.drop(s, false);
            else player.placeItemBackInInventory(s);
        }
    }

    /** 바닐라 tryMoveItems: 고른 거래의 값만큼 인벤토리에서 지불 칸으로 옮겨 놓는다(칸이 둘 다 비었을 때). */
    public void tryMoveItems(int index) {
        var offers = trader.offers();
        if (index < 0 || index >= offers.size()) return;
        ItemStack a = tradeContainer.get(0);
        if (!a.isEmpty()) {
            Moved m = moveItemStackTo(a, INV_SLOT_START, USE_ROW_END, true);
            if (!m.changed()) return;
            tradeContainer.set(0, m.remaining());
        }
        ItemStack b = tradeContainer.get(1);
        if (!b.isEmpty()) {
            Moved m = moveItemStackTo(b, INV_SLOT_START, USE_ROW_END, true);
            if (!m.changed()) return;
            tradeContainer.set(1, m.remaining());
        }
        if (tradeContainer.get(0).isEmpty() && tradeContainer.get(1).isEmpty()) {
            MerchantOffer offer = offers.get(index);
            moveFromInventoryToPaymentSlot(0, offer.baseCostA(), offer.costACount());
            offer.costB().ifPresent(c -> moveFromInventoryToPaymentSlot(1, c, c.count()));
        }
    }

    private void moveFromInventoryToPaymentSlot(int paymentSlot, ItemCost cost, int unused) {
        for (int i = INV_SLOT_START; i < USE_ROW_END; i++) {
            Slot sl = slots.get(i);
            ItemStack stack = sl.getItem();
            if (!stack.isEmpty() && cost.test(stack)) {
                ItemStack payment = tradeContainer.get(paymentSlot);
                if (payment.isEmpty() || Stacks.isSameItemSameComponents(stack, payment)) {
                    int max = Stacks.maxStack(stack);
                    int n = Math.min(max - payment.count(), stack.count());
                    ItemStack copy = stack.withCount(payment.count() + n);
                    ItemStack rest = stack.withCount(stack.count() - n);
                    if (rest.isEmpty()) sl.setByPlayer(ItemStack.EMPTY);
                    else sl.setCountInPlace(rest);
                    tradeContainer.set(paymentSlot, copy);
                    if (copy.count() >= max) break;
                }
            }
        }
    }

    /** 바닐라 MerchantContainer: 칸 3개 + 지금 맞는 거래. */
    public static final class TradeContainer implements Container {
        private final Merchant merchant;
        private final ItemStack[] items = {ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY};
        private MerchantOffer activeOffer;
        private int selectionHint;
        private int futureXp;

        TradeContainer(Merchant merchant) { this.merchant = merchant; }

        @Override public int size() { return 3; }

        @Override public ItemStack get(int i) { return items[i]; }

        public MerchantOffer activeOffer() { return activeOffer; }

        public int futureXp() { return futureXp; }

        private static boolean isPaymentSlot(int i) { return i == 0 || i == 1; }

        @Override public ItemStack remove(int index, int amount) {
            ItemStack cur = items[index];
            if (index == 2 && !cur.isEmpty()) {   // 결과는 어떤 amount 로 빼도 통째로
                items[2] = ItemStack.EMPTY;
                return cur;
            }
            if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
            int n = Math.min(amount, cur.count());
            items[index] = cur.withCount(cur.count() - n);
            ItemStack out = cur.withCount(n);
            if (!out.isEmpty() && isPaymentSlot(index)) updateSellItem();
            return out;
        }

        @Override public ItemStack removeNoUpdate(int index) {
            ItemStack cur = items[index];
            items[index] = ItemStack.EMPTY;
            return cur;
        }

        @Override public void set(int index, ItemStack stack) {
            items[index] = stack == null ? ItemStack.EMPTY : stack;
            if (isPaymentSlot(index)) updateSellItem();
        }

        @Override public void setChanged() { updateSellItem(); }

        @Override public boolean stillValid(MenuPlayer player) { return merchant.tradingPlayer() == player; }

        public void setSelectionHint(int hint) {
            selectionHint = hint;
            updateSellItem();
        }

        /** 바닐라 updateSellItem. */
        public void updateSellItem() {
            activeOffer = null;
            ItemStack a, b;
            if (items[0].isEmpty()) {
                a = items[1];
                b = ItemStack.EMPTY;
            } else {
                a = items[0];
                b = items[1];
            }
            if (a.isEmpty()) {
                items[2] = ItemStack.EMPTY;
                futureXp = 0;
            } else {
                var offers = merchant.offers();
                if (!offers.isEmpty()) {
                    MerchantOffer offer = offers.getRecipeFor(a, b, selectionHint);
                    if (offer == null || offer.isOutOfStock()) {
                        activeOffer = offer;
                        offer = offers.getRecipeFor(b, a, selectionHint);
                    }
                    if (offer != null && !offer.isOutOfStock()) {
                        activeOffer = offer;
                        items[2] = offer.assemble();
                        futureXp = offer.xp();
                    } else {
                        items[2] = ItemStack.EMPTY;
                        futureXp = 0;
                    }
                }
                merchant.notifyTradeUpdated(items[2]);
            }
        }
    }

    /** 바닐라 MerchantResultSlot. */
    private static final class MerchantResultSlot extends Slot {
        private final MenuPlayer player;
        private final Merchant merchant;
        private final TradeContainer slots;
        private int removeCount;

        MerchantResultSlot(MenuPlayer player, Merchant merchant, TradeContainer slots, int index) {
            super(slots, index);
            this.player = player;
            this.merchant = merchant;
            this.slots = slots;
        }

        @Override public boolean mayPlace(ItemStack stack) { return false; }

        @Override public ItemStack remove(int amount) {
            if (hasItem()) removeCount += Math.min(amount, getItem().count());
            return super.remove(amount);
        }

        @Override protected void onQuickCraft(ItemStack stack, int count) {
            removeCount += count;
            checkTakeAchievements(stack);
        }

        @Override protected void checkTakeAchievements(ItemStack stack) {
            player.onItemCrafted(stack, removeCount);
            removeCount = 0;
        }

        @Override
        public void onTake(MenuPlayer p, ItemStack stack) {
            checkTakeAchievements(stack);
            MerchantOffer offer = slots.activeOffer();
            if (offer != null) {
                ItemStack a = slots.get(0), b = slots.get(1);
                ItemStack[] taken = offer.take(a, b);
                if (taken == null) taken = swap(offer.take(b, a));
                if (taken != null) {
                    merchant.notifyTrade(offer);
                    // 바닐라 AbstractVillager.notifyTrade 끝: villager_trade(거래 결과)
                    var ep = p.entityPlayer();
                    var adv = ep == null ? null : ep.advancements();
                    if (adv != null && merchant instanceof org.domi.furnace.server.entity.LivingEntity le) {
                        adv.triggerTrade(le.lootEntity(), org.domi.furnace.server.ServerCore.lootStackOf(offer.result()));
                    }
                    p.awardStat("minecraft:traded_with_villager", 1);
                    slots.set(0, taken[0]);
                    slots.set(1, taken[1]);
                }
                merchant.overrideXp(merchant.villagerXp() + offer.xp());
            }
        }

        private static ItemStack[] swap(ItemStack[] t) { return t == null ? null : new ItemStack[]{t[1], t[0]}; }
    }
}
