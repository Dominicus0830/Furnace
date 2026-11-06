// 들고 있는 단축 칸을 바꿀 때(휠·숫자 키) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 이전 칸으로 되돌린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;

public final class PlayerItemHeldEvent extends PlayerEvent implements Cancellable {

    private final int previousSlot;
    private final int newSlot;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerItemHeldEvent(Player player, int previousSlot, int newSlot) {
        super(player);
        this.previousSlot = previousSlot;
        this.newSlot = newSlot;
    }

    /** 이전 단축 칸(0~8). */
    public int previousSlot() { return previousSlot; }

    /** 새 단축 칸(0~8). */
    public int newSlot() { return newSlot; }
}
