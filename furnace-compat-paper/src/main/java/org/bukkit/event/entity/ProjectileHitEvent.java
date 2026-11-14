package org.bukkit.event.entity;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/** 투사체가 엔티티나 블록에 맞는다. 취소하면 이번 맞음은 없던 일(투사체는 그대로 난다). */
public class ProjectileHitEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;
    private final Entity hitEntity;
    private final Block hitBlock;
    private final BlockFace hitFace;

    public ProjectileHitEvent(Projectile projectile, Entity hitEntity, Block hitBlock, BlockFace hitFace) {
        super(projectile);
        this.hitEntity = hitEntity;
        this.hitBlock = hitBlock;
        this.hitFace = hitFace;
    }

    public ProjectileHitEvent(Projectile projectile) { this(projectile, null, null, null); }

    public ProjectileHitEvent(Projectile projectile, Entity hitEntity) { this(projectile, hitEntity, null, null); }

    public ProjectileHitEvent(Projectile projectile, Block hitBlock) { this(projectile, null, hitBlock, null); }

    public ProjectileHitEvent(Projectile projectile, Entity hitEntity, Block hitBlock) { this(projectile, hitEntity, hitBlock, null); }

    @Override
    public Projectile getEntity() { return (Projectile) entity; }

    public Entity getHitEntity() { return hitEntity; }

    public Block getHitBlock() { return hitBlock; }

    public BlockFace getHitBlockFace() { return hitFace; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
