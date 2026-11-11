package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** 발사기·공급기가 아이템을 내보낸다. Furnace 는 아직 알리지 않는다. */
public class BlockDispenseEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private ItemStack item;
    private Vector velocity;

    public BlockDispenseEvent(Block block, ItemStack dispensed, Vector velocity) {
        super(block);
        this.item = dispensed;
        this.velocity = velocity;
    }

    public ItemStack getItem() { return item.clone(); }

    public void setItem(ItemStack item) { this.item = item; }

    public Vector getVelocity() { return velocity.clone(); }

    public void setVelocity(Vector vel) { this.velocity = vel.clone(); }
}
