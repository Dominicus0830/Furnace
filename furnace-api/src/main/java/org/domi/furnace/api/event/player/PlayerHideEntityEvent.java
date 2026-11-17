// 플레이어에게서 엔티티가 숨겨졌다(Paper·Bukkit PlayerHideEntityEvent 와 같은 자리): 플러그인이 숨겨 보이지 않게 됐을 때(EntityViews.hide·
// setVisibleByDefault) 그 호출 스레드에서 불린다. 프로필 교체의 일시 해제에서는 부르지 않는다(Paper 와 같다). 취소할 수 없다.
// 구독자가 없으면 서버는 이 이벤트를 만들지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;

public final class PlayerHideEntityEvent extends PlayerEvent {

    private final Entity entity;

    public PlayerHideEntityEvent(Player player, Entity entity) {
        super(player);
        this.entity = entity;
    }

    /** 이제 숨겨진 엔티티. */
    public Entity entity() { return entity; }
}
