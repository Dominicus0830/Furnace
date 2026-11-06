// 음식·물약·우유 등을 다 먹기(마시기) 직전 — 플레이어를 가진 region 스레드에서 불린다. 취소하면 효과도 소비도 없고 손 칸을 다시 보낸다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class PlayerItemConsumeEvent extends PlayerEvent implements Cancellable {

    private final ItemStack item;
    private final boolean mainHand;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerItemConsumeEvent(Player player, ItemStack item, boolean mainHand) {
        super(player);
        this.item = item;
        this.mainHand = mainHand;
    }

    public ItemStack item() { return item; }

    /** 주 손이면 true, 보조 손이면 false. */
    public boolean isMainHand() { return mainHand; }
}
