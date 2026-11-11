package org.bukkit.event.entity;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 엔티티가 블록을 바꾼다(엔더맨·떨어지는 블록·양 등). Furnace 는 아직 알리지 않는다. */
public class EntityChangeBlockEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final Block block;
    private final BlockData to;

    public EntityChangeBlockEvent(Entity what, Block block, BlockData to) {
        super(what);
        this.block = block;
        this.to = to;
    }

    public Block getBlock() { return block; }

    public Material getTo() { return to.getMaterial(); }

    public BlockData getBlockData() { return to; }
}
