package org.bukkit.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.Item;

/**
 * 아이템 엔티티가 나타나려 한다. Furnace 는 아이템 엔티티를 Item 으로 보이지 못해 아직 알리지 않는다.
 * Bukkit 처럼 처리기 목록은 {@link EntitySpawnEvent} 것을 같이 쓴다(EntitySpawnEvent 리스너도 아이템 스폰을 받는다).
 */
public class ItemSpawnEvent extends EntitySpawnEvent {

    public ItemSpawnEvent(Item spawnee) { super(spawnee); }

    /** 위치 인자는 쓰지 않는다(Bukkit 에서도 폐기 예정 — 엔티티 위치를 쓴다). */
    @Deprecated
    public ItemSpawnEvent(Item spawnee, Location loc) { this(spawnee); }

    @Override
    public Item getEntity() { return (Item) entity; }
}
