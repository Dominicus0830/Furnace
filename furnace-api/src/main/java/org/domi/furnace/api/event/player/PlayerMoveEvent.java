// 플레이어가 움직인 직후(서버 검증 통과 뒤) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 from 으로 되돌린다(보정 텔레포트).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Location;

public final class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private final Location from, to;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerMoveEvent(Player player, Location from, Location to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location from() { return from; }

    public Location to() { return to; }

    public boolean hasChangedBlock() { return from.blockX() != to.blockX() || from.blockY() != to.blockY() || from.blockZ() != to.blockZ(); }
}
