package org.bukkit.event.entity;

import org.bukkit.entity.Item;
import org.bukkit.event.HandlerList;

/** 아이템 엔티티가 나타나려 한다. Furnace 는 아이템 엔티티를 Item 으로 보이지 못해 아직 알리지 않는다. */
public class ItemSpawnEvent extends EntitySpawnEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    public ItemSpawnEvent(Item spawnee) { super(spawnee); }

    @Override
    public Item getEntity() { return (Item) entity; }
}
