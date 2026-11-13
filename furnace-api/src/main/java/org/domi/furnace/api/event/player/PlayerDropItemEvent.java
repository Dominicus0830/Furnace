// 플레이어가 아이템을 떨구기 직전 — 플레이어를 가진 region 스레드에서 불린다. 취소하면 인벤토리로 돌려준다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.item.ItemStack;

public final class PlayerDropItemEvent extends PlayerEvent implements Cancellable {

    private final ItemStack item;
    private final Entity entity;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerDropItemEvent(Player player, ItemStack item) { this(player, item, null); }

    /** entity 는 던질 아이템 엔티티(아직 월드에 넣기 전, CraftBukkit 과 같다). 모르면 null. */
    public PlayerDropItemEvent(Player player, ItemStack item, Entity entity) {
        super(player);
        this.item = item;
        this.entity = entity;
    }

    /** 곧 월드에 들어갈 아이템 엔티티(이 이벤트가 취소되면 들어가지 않는다). 서버가 만들지 못한 경로면 null. */
    public Entity entity() { return entity; }

    public ItemStack item() { return item; }
}
