package org.bukkit.event.entity;

import java.util.List;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;

/**
 * 엔티티(크리퍼·TNT·가스트 불덩이 등)가 터진다. blockList() 를 고치면 서버가 그 목록의 블록을 부순다(넣은 칸도 부서진다). 취소하면 블록·불·패킷 모두 없다.
 * setYield 는 서버가 적용하지 않는다(읽기만 — BlockExplodeEvent 와 같은 한계).
 */
public class EntityExplodeEvent extends EntityEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private boolean cancelled;
    private final Location location;
    private final List<Block> blocks;
    private float yield;
    private final ExplosionResult result;

    public EntityExplodeEvent(Entity what, Location location, List<Block> blocks, float yield, ExplosionResult result) {
        super(what);
        this.location = location;
        this.blocks = blocks;
        this.yield = yield;
        this.result = result;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public ExplosionResult getExplosionResult() { return result; }

    public List<Block> blockList() { return blocks; }

    public Location getLocation() { return location.clone(); }

    public float getYield() { return yield; }

    public void setYield(float yield) { this.yield = yield; }
}
