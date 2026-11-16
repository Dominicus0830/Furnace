package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 블록이 자란다(작물·줄기·사탕수수 등). getNewState() 는 자란 뒤의 스냅숏이다. */
public class BlockGrowEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final BlockState newState;

    public BlockGrowEvent(Block block, BlockState newState) {
        super(block);
        this.newState = newState;
    }

    public BlockState getNewState() { return newState; }
}
