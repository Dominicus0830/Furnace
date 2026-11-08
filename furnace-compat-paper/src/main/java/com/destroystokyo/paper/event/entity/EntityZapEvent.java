package com.destroystokyo.paper.event.entity;

import java.util.Collections;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityTransformEvent;

/** 번개를 맞은 엔티티가 바뀌기 직전(Paper). Furnace 에는 맞는 이벤트가 아직 없어 불리지 않는다. */
public class EntityZapEvent extends EntityTransformEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private boolean cancelled;
    private final LightningStrike bolt;
    private final Entity replacementEntity;

    public EntityZapEvent(Entity entity, LightningStrike bolt, Entity replacementEntity) {
        super(entity, Collections.singletonList(replacementEntity), TransformReason.LIGHTNING);
        this.bolt = bolt;
        this.replacementEntity = replacementEntity;
    }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    public LightningStrike getBolt() { return bolt; }

    public Entity getReplacementEntity() { return replacementEntity; }

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
