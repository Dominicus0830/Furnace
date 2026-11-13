// 플레이어가 엔티티를 보기 시작하는 순간(스폰 묶음 add_entity·메타데이터·속성 … 을 그 플레이어에게 보내기 바로 전) — 그 엔티티를 가진 region 스레드에서 불린다.
// Paper PlayerTrackEntityEvent 와 같은 자리다. 플레이어가 다른 플레이어를 보기 시작할 때는 아직 부르지 않는다. 지금은 취소할 수 없다.
// 구독자가 없으면 서버는 이 이벤트를 만들지 않는다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.entity.Player;

public final class PlayerTrackEntityEvent extends PlayerEvent {

    private final Entity entity;

    public PlayerTrackEntityEvent(Player player, Entity entity) {
        super(player);
        this.entity = entity;
    }

    /** 이제 보이는 엔티티(이 순간의 스냅샷). */
    public Entity entity() { return entity; }
}
