// 웅크림(Shift)을 누르거나 뗀 직후(클라이언트 패킷) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 서버 쪽 웅크리기 상태를 바꾸지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerToggleSneakEvent extends PlayerEvent implements Cancellable {

    private final boolean sneaking;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerToggleSneakEvent(Player player, boolean sneaking) {
        super(player);
        this.sneaking = sneaking;
    }

    /** 바뀔 상태(true = 켜짐). */
    public boolean isSneaking() { return sneaking; }
}
