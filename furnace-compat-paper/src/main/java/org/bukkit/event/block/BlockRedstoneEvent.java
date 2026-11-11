package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.event.HandlerList;

/** 레드스톤 세기가 바뀐다. 새 세기를 바꿀 수 있다. Furnace 서버의 레드스톤 이벤트와 이어진다. */
public class BlockRedstoneEvent extends BlockEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private final int oldCurrent;
    private int newCurrent;

    public BlockRedstoneEvent(Block block, int oldCurrent, int newCurrent) {
        super(block);
        this.oldCurrent = oldCurrent;
        this.newCurrent = newCurrent;
    }

    public int getOldCurrent() { return oldCurrent; }

    public int getNewCurrent() { return newCurrent; }

    public void setNewCurrent(int newCurrent) { this.newCurrent = newCurrent; }
}
