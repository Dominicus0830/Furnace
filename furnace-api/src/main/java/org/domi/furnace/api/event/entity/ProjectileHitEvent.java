// 투사체가 블록이나 엔티티에 맞기 직전 — 투사체를 가진 region 스레드에서 불린다. 취소하면 이번 적중을 무시하고 계속 날아간다(Paper 와 같다).
// 엔티티에 맞았으면 hitEntity, 블록이면 hitBlock·hitFace 가 채워진다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Block;

public final class ProjectileHitEvent extends EntityEvent implements Cancellable {

    private final Entity hitEntity;
    private final Block hitBlock;
    private final String hitFace;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public ProjectileHitEvent(Entity projectile, Entity hitEntity, Block hitBlock, String hitFace) {
        super(projectile);
        this.hitEntity = hitEntity;
        this.hitBlock = hitBlock;
        this.hitFace = hitFace;
    }

    public Entity hitEntity() { return hitEntity; }

    public Block hitBlock() { return hitBlock; }

    /** 맞은 면(down, up, north, south, west, east) — 블록 적중이 아니면 null. */
    public String hitFace() { return hitFace; }
}
