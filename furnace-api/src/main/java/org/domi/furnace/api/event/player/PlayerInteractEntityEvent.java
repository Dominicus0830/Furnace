// 플레이어가 엔티티를 오른쪽 클릭했을 때(interact 패킷의 INTERACT) — 그 엔티티를 가진 region 스레드에서, 바닐라 처리 전에 불린다.
// 취소하면 상호작용(거래 창·탑승·이름표·양동이 등)이 일어나지 않고 손 아이템을 다시 보낸다. INTERACT_AT(갑옷 거치대 위치 클릭)은 발화하지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class PlayerInteractEntityEvent extends PlayerEvent implements Cancellable {

    private final Entity target;
    private final ItemStack item;
    private final boolean mainHand;
    private boolean cancelled;

    public PlayerInteractEntityEvent(Player player, Entity target, ItemStack item, boolean mainHand) {
        super(player);
        this.target = target;
        this.item = item;
        this.mainHand = mainHand;
    }

    /** 클릭한 엔티티(이벤트 시점 스냅샷). */
    public Entity target() { return target; }

    /** 그 손에 든 아이템(빈 손이면 EMPTY). */
    public ItemStack item() { return item; }

    public boolean isMainHand() { return mainHand; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
