// 플레이어가 바닥 아이템을 줍기 직전 — 그 아이템 엔티티를 가진 region 스레드에서 불린다. 취소하면 줍지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class PlayerPickupItemEvent extends PlayerEvent implements Cancellable {

    private final ItemStack item;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerPickupItemEvent(Player player, ItemStack item) {
        super(player);
        this.item = item;
    }

    /** 줍는 스택(바닥 아이템 전체). */
    public ItemStack item() { return item; }
}
