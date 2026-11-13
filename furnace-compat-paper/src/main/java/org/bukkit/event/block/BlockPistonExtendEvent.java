package org.bukkit.event.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.HandlerList;

/** 피스톤이 늘어난다. 취소하면 서버가 밀기를 하지 않는다. getBlocks() 는 밀리거나 부서질 블록(바꿀 수 없다). */
public class BlockPistonExtendEvent extends BlockPistonEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private final int length;
    private List<Block> blocks;

    /** @deprecated 1.8 부터 getBlocks 를 쓴다. */
    @Deprecated
    public BlockPistonExtendEvent(Block block, int length, BlockFace direction) {
        super(block, direction);
        this.length = length;
    }

    public BlockPistonExtendEvent(Block block, List<Block> blocks, BlockFace direction) {
        super(block, direction);
        this.length = blocks.size();
        this.blocks = blocks;
    }

    /** @deprecated getBlocks().size() */
    @Deprecated
    public int getLength() { return length; }

    /** 밀리거나 부서질 블록(바꿀 수 없는 목록). */
    public List<Block> getBlocks() {
        if (blocks == null) {
            List<Block> tmp = new ArrayList<>();
            for (int i = 0; i < length; i++) tmp.add(block.getRelative(getDirection(), i + 1));
            blocks = Collections.unmodifiableList(tmp);
        }
        return blocks;
    }
}
