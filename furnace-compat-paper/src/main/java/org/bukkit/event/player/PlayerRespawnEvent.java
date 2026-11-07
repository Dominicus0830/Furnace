package org.bukkit.event.player;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;

public class PlayerRespawnEvent extends PlayerEvent {

    private Location respawnLocation;
    private final boolean isBedSpawn;

    public PlayerRespawnEvent(Player respawnPlayer, Location respawnLocation, boolean isBedSpawn) {
        super(respawnPlayer);
        this.respawnLocation = respawnLocation;
        this.isBedSpawn = isBedSpawn;
    }

    public Location getRespawnLocation() { return respawnLocation; }

    public void setRespawnLocation(Location respawnLocation) {
        if (respawnLocation == null || respawnLocation.getWorld() == null) throw new IllegalArgumentException("respawnLocation 에 월드가 있어야 한다");
        this.respawnLocation = respawnLocation;
    }

    /** 침대·리스폰 닻 자리에서 되살아나는가(월드 스폰이 아니다). */
    public boolean isBedSpawn() { return isBedSpawn; }

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }
}
