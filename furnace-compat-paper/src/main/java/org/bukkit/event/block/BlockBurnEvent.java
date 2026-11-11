package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 블록이 불에 타 사라진다. Furnace 서버의 연소 이벤트와 이어진다. */
public class BlockBurnEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final Block ignitingBlock;

    @Deprecated
    public BlockBurnEvent(Block block) { this(block, null); }

    public BlockBurnEvent(Block block, Block ignitingBlock) {
        super(block);
        this.ignitingBlock = ignitingBlock;
    }

    /** 태운 불 블록. 모르면 null. */
    public Block getIgnitingBlock() { return ignitingBlock; }
}
