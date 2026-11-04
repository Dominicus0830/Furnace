// 투사체(화살·눈덩이·화염구·삼지창 등)가 월드에 들어가기 직전 — 넣는 region 스레드에서 불린다. 취소하면 넣지 않는다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;

public final class ProjectileLaunchEvent extends EntitySpawnEvent {

    public ProjectileLaunchEvent(Entity projectile) { super(projectile); }
}
