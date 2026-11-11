package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** 플레이어가 블록을 캐기 시작했다. Furnace 는 아직 알리지 않는다. */
public class BlockDamageEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final Player player;
    private final ItemStack itemstack;
    private boolean instaBreak;

    public BlockDamageEvent(Player player, Block block, ItemStack itemInHand, boolean instaBreak) {
        super(block);
        this.player = player;
        this.itemstack = itemInHand;
        this.instaBreak = instaBreak;
    }

    public Player getPlayer() { return player; }

    public boolean getInstaBreak() { return instaBreak; }

    public void setInstaBreak(boolean bool) { this.instaBreak = bool; }

    public ItemStack getItemInHand() { return itemstack; }
}
