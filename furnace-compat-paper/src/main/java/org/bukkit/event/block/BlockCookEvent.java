package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/** 화로·모닥불 등이 아이템을 익혔다. 화로(FurnaceSmeltEvent)만 Furnace 서버의 제련 이벤트와 이어진다. */
public class BlockCookEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final ItemStack source;
    private ItemStack result;

    public BlockCookEvent(Block block, ItemStack source, ItemStack result) {
        super(block);
        this.source = source;
        this.result = result;
    }

    public ItemStack getSource() { return source; }

    public ItemStack getResult() { return result; }

    public void setResult(ItemStack result) { this.result = result; }
}
