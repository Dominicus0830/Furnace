// 달리기를 시작하거나 멈출 때(클라이언트 패킷) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 서버 쪽 달리기 상태를 바꾸지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerToggleSprintEvent extends PlayerEvent implements Cancellable {

    private final boolean sprinting;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerToggleSprintEvent(Player player, boolean sprinting) {
        super(player);
        this.sprinting = sprinting;
    }

    /** 바뀔 상태(true = 켜짐). */
    public boolean isSprinting() { return sprinting; }
}
