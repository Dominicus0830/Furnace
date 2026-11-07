package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

public class BlockBreakEvent extends BlockEvent implements Cancellable {

    private final Player player;
    private boolean cancelled;

    public BlockBreakEvent(Block theBlock, Player player) {
        super(theBlock);
        this.player = player;
    }

    public Player getPlayer() { return player; }

    /** 드롭 끄기·경험치 조절은 Furnace 의 블록 파괴 이벤트가 아직 노출하지 않는다(H3). */
    public boolean isDropItems() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#isDropItems", "블록 파괴 이벤트가 드롭 제어를 노출하지 않는다");
    }

    public void setDropItems(boolean dropItems) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#setDropItems", "블록 파괴 이벤트가 드롭 제어를 노출하지 않는다");
    }

    public int getExpToDrop() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#getExpToDrop", "블록 파괴 이벤트가 경험치 제어를 노출하지 않는다");
    }

    public void setExpToDrop(int exp) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#setExpToDrop", "블록 파괴 이벤트가 경험치 제어를 노출하지 않는다");
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
