package org.bukkit.event.block;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 블록이 이웃 갱신을 받는다. Furnace 서버의 이웃 갱신 이벤트와 이어진다. */
public class BlockPhysicsEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final BlockData changed;
    private final Block sourceBlock;

    public BlockPhysicsEvent(Block block, BlockData changed) { this(block, changed, block); }

    public BlockPhysicsEvent(Block block, BlockData changed, Block sourceBlock) {
        super(block);
        this.changed = changed;
        this.sourceBlock = sourceBlock;
    }

    /** 갱신을 일으킨 칸. */
    public Block getSourceBlock() { return sourceBlock; }

    /** 갱신을 받는 블록의 종류(CraftBukkit 은 갱신받는 칸의 상태를 넘긴다). */
    public Material getChangedType() { return changed.getMaterial(); }

    public BlockData getChangedBlockData() { return changed.clone(); }
}
