// 떨어진 아이템 엔티티가 월드에 들어가기 직전(블록 부수기·버리기·몹 전리품 등) — 넣는 region 스레드에서 불린다. 취소하면 넣지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;

public final class ItemSpawnEvent extends EntitySpawnEvent {

    public ItemSpawnEvent(Entity item) { super(item); }
}
