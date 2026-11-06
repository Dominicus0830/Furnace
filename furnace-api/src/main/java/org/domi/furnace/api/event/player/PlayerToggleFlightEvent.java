// 크리에이티브 등 비행 가능 플레이어가 날기를 켜거나 끌 때(클라이언트 패킷) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 서버 쪽 날기 상태를 바꾸지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerToggleFlightEvent extends PlayerEvent implements Cancellable {

    private final boolean flying;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerToggleFlightEvent(Player player, boolean flying) {
        super(player);
        this.flying = flying;
    }

    /** 바뀔 상태(true = 켜짐). */
    public boolean isFlying() { return flying; }
}
