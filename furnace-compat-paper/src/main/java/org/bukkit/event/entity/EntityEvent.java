package org.bukkit.event.entity;

import org.bukkit.entity.Entity;
import org.bukkit.event.Event;

public abstract class EntityEvent extends Event {

    protected Entity entity;

    public EntityEvent(Entity what) { this.entity = what; }

    public Entity getEntity() { return entity; }

    /** Bukkit: 엔티티 종류(getEntity().getType()). */
    public org.bukkit.entity.EntityType getEntityType() { return entity.getType(); }
}
