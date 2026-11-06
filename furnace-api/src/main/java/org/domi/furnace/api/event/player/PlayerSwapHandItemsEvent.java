// 두 손의 아이템을 바꾸기 직전(F 키) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 바꾸지 않는다.
// 바꾼 뒤 각 손에 들어갈 아이템을 고칠 수 있다(null 은 빈 손).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class PlayerSwapHandItemsEvent extends PlayerEvent implements Cancellable {

    private ItemStack mainHandItem;
    private ItemStack offHandItem;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerSwapHandItemsEvent(Player player, ItemStack mainHandItem, ItemStack offHandItem) {
        super(player);
        this.mainHandItem = mainHandItem;
        this.offHandItem = offHandItem;
    }

    /** 바꾼 뒤 주 손에 들 아이템(원래 보조 손 아이템). */
    public ItemStack mainHandItem() { return mainHandItem; }

    public void setMainHandItem(ItemStack item) { this.mainHandItem = item; }

    /** 바꾼 뒤 보조 손에 들 아이템(원래 주 손 아이템). */
    public ItemStack offHandItem() { return offHandItem; }

    public void setOffHandItem(ItemStack item) { this.offHandItem = item; }
}
