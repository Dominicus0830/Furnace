package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 물·용암이 흐르거나 용 알이 순간이동한다. Furnace 서버의 흐름 이벤트와 이어진다(용 알은 아직 아님). */
public class BlockFromToEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    protected Block to;
    protected BlockFace face;

    public BlockFromToEvent(Block block, BlockFace face) {
        super(block);
        this.face = face;
    }

    public BlockFromToEvent(Block block, Block toBlock) {
        super(block);
        this.to = toBlock;
        this.face = BlockFace.SELF;
    }

    public BlockFace getFace() { return face; }

    public Block getToBlock() {
        if (to == null) to = block.getRelative(face);
        return to;
    }
}
