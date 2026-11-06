// 접속할 때 첫 위치를 정하기 직전(PlayerCreateEvent 다음, 클라이언트에게 차원을 알리기 전) — 연결 스레드에서 불린다.
// 기본값은 바닐라와 같다: 저장된 위치(없으면 월드 스폰 근처). 다른 차원(이 서버에 있는 것)·좌표·시선으로 바꿀 수 있다.
// 없는 차원이면 무시하고 기본 위치로 들어간다. 블로킹 작업 금지.
package org.domi.furnace.api.event.player;

import java.util.Objects;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.world.Location;

public final class PlayerSpawnLocationEvent extends PlayerEvent {

    private final boolean firstJoin;
    private Location spawnLocation;

    public PlayerSpawnLocationEvent(Player player, Location spawnLocation, boolean firstJoin) {
        super(player);
        this.spawnLocation = Objects.requireNonNull(spawnLocation, "spawnLocation");
        this.firstJoin = firstJoin;
    }

    public Location spawnLocation() { return spawnLocation; }

    public void spawnLocation(Location location) { this.spawnLocation = Objects.requireNonNull(location, "location"); }

    /** 저장 데이터가 없는(처음 들어오는) 플레이어인가. */
    public boolean isFirstJoin() { return firstJoin; }
}
