package org.bukkit.event.world;

import org.bukkit.World;
import org.bukkit.event.HandlerList;

/** 월드를 만든 직후·불러오기 전. Furnace 는 아직 알리지 않는다(월드가 플러그인보다 먼저 준비된다). */
public class WorldInitEvent extends WorldEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    public WorldInitEvent(World world) { super(world); }
}
