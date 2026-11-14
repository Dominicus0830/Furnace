package org.bukkit.event.block;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.HandlerList;

/** 피스톤이 줄어든다(끈끈이 피스톤은 끌 블록이 있다). 취소하면 서버가 끌기를 하지 않는다. */
public class BlockPistonRetractEvent extends BlockPistonEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private final List<Block> blocks;

    public BlockPistonRetractEvent(Block block, List<Block> blocks, BlockFace direction) {
        super(block, direction);
        this.blocks = blocks;
    }

    /** @deprecated getBlocks 를 쓴다. */
    @Deprecated
    public Location getRetractLocation() { return getBlock().getRelative(getDirection(), 2).getLocation(); }

    /** 끌려 올 블록(바꿀 수 없는 목록). */
    public List<Block> getBlocks() { return blocks; }
}
