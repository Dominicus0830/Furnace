package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.HandlerList;

/** 블록이 퍼진다(잔디·균사체·버섯·덩굴·불). getBlock() 은 새로 생기는 자리, getSource() 는 퍼져 나온 블록. BlockFormEvent 처리기에는 오지 않는다. */
public class BlockSpreadEvent extends BlockFormEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private final Block source;

    public BlockSpreadEvent(Block block, Block source, BlockState newState) {
        super(block, newState);
        this.source = source;
    }

    public Block getSource() { return source; }
}
