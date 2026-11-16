// 플레이어에게 엔티티가 다시 보이게 됐다(Paper·Bukkit PlayerShowEntityEvent 와 같은 자리): 플러그인이 숨김을 거둬 보이게 됐을 때(EntityViews.show·
// setVisibleByDefault) 그 호출 스레드에서, 다른 플레이어가 프로필을 바꿔(Player#profile(ProfileData)) 탭·엔티티를 다시 올릴 때 관찰자 region 스레드에서
// 불린다(본인도 받는다). 취소할 수 없다. 구독자가 없으면 서버는 이 이벤트를 만들지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;

public final class PlayerShowEntityEvent extends PlayerEvent {

    private final Entity entity;

    public PlayerShowEntityEvent(Player player, Entity entity) {
        super(player);
        this.entity = entity;
    }

    /** 이제 보이는 엔티티. */
    public Entity entity() { return entity; }
}
