package org.bukkit.event.enchantment;

import org.bukkit.block.Block;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.EnchantmentView;

/** 마법 부여대가 제안 세 가지를 정해 보여 주기 직전. getOffers() 의 칸을 고치면(null 은 제안 없음) 그대로 보인다. 취소하면 세 칸 모두 비운다. */
public class PrepareItemEnchantEvent extends InventoryEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Block table;
    private final ItemStack item;
    private final EnchantmentOffer[] offers;
    private final int bonus;
    private boolean cancelled;
    private final Player enchanter;

    public PrepareItemEnchantEvent(Player enchanter, EnchantmentView view, Block table, ItemStack item, EnchantmentOffer[] offers, int bonus) {
        super(view);
        this.enchanter = enchanter;
        this.table = table;
        this.item = item;
        this.offers = offers;
        this.bonus = bonus;
    }

    @Override
    public EnchantmentView getView() { return (EnchantmentView) super.getView(); }

    public Player getEnchanter() { return enchanter; }

    public Block getEnchantBlock() { return table; }

    public ItemStack getItem() { return item; }

    /** 제안마다 비용(없는 칸은 0). 1.20.5 부터는 {@link #getOffers()} 를 쓴다. */
    @Deprecated(since = "1.20.5")
    public int[] getExpLevelCostsOffered() {
        int[] levelOffers = new int[offers.length];
        for (int i = 0; i < offers.length; i++) levelOffers[i] = offers[i] != null ? offers[i].getCost() : 0;
        return levelOffers;
    }

    public EnchantmentOffer[] getOffers() { return offers; }

    /** 둘레 책장 수(최대 15). */
    public int getEnchantmentBonus() { return bonus; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
