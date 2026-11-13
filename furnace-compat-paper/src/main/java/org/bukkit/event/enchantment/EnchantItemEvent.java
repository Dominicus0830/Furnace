package org.bukkit.event.enchantment;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

/** 마법 부여대의 제안을 눌러 마법을 부여하기 직전. getEnchantsToAdd() 를 고치면 그대로 부여된다. */
public class EnchantItemEvent extends InventoryEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final Block table;
    private ItemStack item;
    private int level;
    private boolean cancelled;
    private final Map<Enchantment, Integer> enchants;
    private final Enchantment enchantmentHint;
    private final int levelHint;
    private final Player enchanter;
    private final int button;

    public EnchantItemEvent(Player enchanter, InventoryView view, Block table, ItemStack item, int level, Map<Enchantment, Integer> enchants,
                            Enchantment enchantmentHint, int levelHint, int button) {
        super(view);
        this.enchanter = enchanter;
        this.table = table;
        this.item = item;
        this.level = level;
        this.enchants = new HashMap<>(enchants);
        this.enchantmentHint = enchantmentHint;
        this.levelHint = levelHint;
        this.button = button;
    }

    public Player getEnchanter() { return enchanter; }

    public Block getEnchantBlock() { return table; }

    public ItemStack getItem() { return item; }

    /** Furnace: 마법을 부여할 아이템을 바꾸는 API 가 없다 — 다른 아이템이면 UnsupportedFeature. */
    public void setItem(ItemStack item) {
        if (java.util.Objects.equals(item, this.item)) return;
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("EnchantItemEvent#setItem", "Furnace EnchantItemEvent 는 마법을 부여할 아이템을 바꾸지 못한다");
    }

    public int getExpLevelCost() { return level; }

    public void setExpLevelCost(int level) {
        if (level <= 0) throw new IllegalArgumentException("The cost must be greater than 0!");
        this.level = level;
    }

    public Map<Enchantment, Integer> getEnchantsToAdd() { return enchants; }

    /** 눌린 제안이 단서로 보여 준 마법 부여(없으면 null). */
    public Enchantment getEnchantmentHint() { return enchantmentHint; }

    /** 눌린 제안이 단서로 보여 준 레벨. */
    public int getLevelHint() { return levelHint; }

    /** 눌린 제안 칸 0~2. */
    public int whichButton() { return button; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
