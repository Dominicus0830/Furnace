package org.bukkit.event.block;

import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 블록에 불이 붙으려 한다. Furnace 서버의 점화 이벤트와 이어진다. */
public class BlockIgniteEvent extends BlockEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
    private final IgniteCause cause;
    private final Entity ignitingEntity;
    private final Block ignitingBlock;

    public BlockIgniteEvent(Block theBlock, IgniteCause cause, Entity ignitingEntity) { this(theBlock, cause, ignitingEntity, null); }

    public BlockIgniteEvent(Block theBlock, IgniteCause cause, Block ignitingBlock) { this(theBlock, cause, null, ignitingBlock); }

    public BlockIgniteEvent(Block theBlock, IgniteCause cause, Entity ignitingEntity, Block ignitingBlock) {
        super(theBlock);
        this.cause = cause;
        this.ignitingEntity = ignitingEntity;
        this.ignitingBlock = ignitingBlock;
    }

    public IgniteCause getCause() { return cause; }

    public Player getPlayer() { return ignitingEntity instanceof Player p ? p : null; }

    public Entity getIgnitingEntity() { return ignitingEntity; }

    public Block getIgnitingBlock() { return ignitingBlock; }

    public enum IgniteCause {
        LAVA, FLINT_AND_STEEL, SPREAD, LIGHTNING, FIREBALL, ENDER_CRYSTAL, EXPLOSION, ARROW
    }
}
