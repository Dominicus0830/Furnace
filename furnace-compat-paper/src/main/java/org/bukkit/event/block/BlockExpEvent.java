package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;

/** 블록이 경험치를 떨어뜨린다. Furnace 는 아직 알리지 않는다. */
public class BlockExpEvent extends BlockEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private int exp;

    public BlockExpEvent(Block block, int exp) {
        super(block);
        this.exp = exp;
    }

    public int getExpToDrop() { return exp; }

    public void setExpToDrop(int exp) { this.exp = exp; }
}
