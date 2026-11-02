// 플레이어가 아이템을 떨구기 직전 — 플레이어를 가진 region 스레드에서 불린다. 취소하면 인벤토리로 돌려준다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class PlayerDropItemEvent extends PlayerEvent implements Cancellable {

    private final ItemStack item;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerDropItemEvent(Player player, ItemStack item) {
        super(player);
        this.item = item;
    }

    public ItemStack item() { return item; }
}
