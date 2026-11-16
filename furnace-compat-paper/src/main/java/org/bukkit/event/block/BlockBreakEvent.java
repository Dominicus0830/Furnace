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

    /**
     * 드롭 끄기·경험치 조절은 Furnace 의 블록 파괴 이벤트가 아직 노출하지 않는다(H3).
     * 읽기는 정직한 값: 취소하지 않으면 늘 떨구므로 true(Paper 기본값과 같다, A15-04). 쓰기는 지원하지 않는다.
     */
    public boolean isDropItems() { return true; }

    public void setDropItems(boolean dropItems) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#setDropItems", "the block break event does not expose drop control");
    }

    public int getExpToDrop() {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#getExpToDrop", "the block break event does not expose experience control");
    }

    public void setExpToDrop(int exp) {
        throw new org.domi.furnace.compat.paper.UnsupportedFeature("BlockBreakEvent#setExpToDrop", "the block break event does not expose experience control");
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
