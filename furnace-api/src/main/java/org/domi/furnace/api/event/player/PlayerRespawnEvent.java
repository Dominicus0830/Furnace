// 리스폰 직전 — 리스폰 위치를 바꿀 수 있다. 연결 스레드에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.world.Location;

public final class PlayerRespawnEvent extends PlayerEvent {

    private Location respawnLocation;
    private final boolean worldSpawn;

    public PlayerRespawnEvent(Player player, Location respawnLocation, boolean worldSpawn) {
        super(player);
        this.respawnLocation = respawnLocation;
        this.worldSpawn = worldSpawn;
    }

    public Location respawnLocation() { return respawnLocation; }

    public void respawnLocation(Location l) { this.respawnLocation = java.util.Objects.requireNonNull(l); }

    /** 개인 스폰 지점이 없거나 무효라 월드 스폰으로 가는가. */
    public boolean isWorldSpawn() { return worldSpawn; }
}
