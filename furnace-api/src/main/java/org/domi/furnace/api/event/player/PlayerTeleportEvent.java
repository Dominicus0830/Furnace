// 서버가 플레이어를 같은 차원 안에서 옮기기 직전 — 텔레포트를 요청한 스레드에서 불린다(region 소유 스레드가 아닐 수 있다). 취소하면 옮기지 않는다.
// 차원 이동·원인 구분은 다음 단계.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Location;

public final class PlayerTeleportEvent extends PlayerEvent implements Cancellable {

    private final Location from;
    private final Location to;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerTeleportEvent(Player player, Location from, Location to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location from() { return from; }

    public Location to() { return to; }
}
