package org.bukkit.event.block;

import java.util.List;
import org.bukkit.ExplosionResult;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 블록이 터진다(침대·리스폰 정박기). blockList() 를 고치면 서버가 따른다. setYield 는 서버가 적용하지 않는다. */
public class BlockExplodeEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final List<Block> blocks;
    private float yield;
    private final BlockState blockState;
    private final ExplosionResult result;

    public BlockExplodeEvent(Block what, BlockState blockState, List<Block> blocks, float yield, ExplosionResult result) {
        super(what);
        this.blockState = blockState;
        this.blocks = blocks;
        this.yield = yield;
        this.result = result;
    }

    public ExplosionResult getExplosionResult() { return result; }

    public BlockState getExplodedBlockState() { return blockState; }

    public List<Block> blockList() { return blocks; }

    public float getYield() { return yield; }

    public void setYield(float yield) { this.yield = yield; }
}
