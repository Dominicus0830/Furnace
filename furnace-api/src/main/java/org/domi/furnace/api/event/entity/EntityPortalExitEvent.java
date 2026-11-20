// 플레이어가 아닌 엔티티가 차원문(지옥문, 엔드 포털, 엔드 관문)을 지나 도착 위치로 옮겨지기 직전에 엔티티의 region 스레드에서 불린다
// (Paper Entity#changeDimension 의 EntityPortalExitEvent — 같은 자리의 EntityTeleportEvent 뒤). 도착 위치(to)와 도착 속도(after)를 바꿀 수 있다.
// 취소하면 이동을 막지 않고 이 이벤트에서 바꾼 값만 버린다(Paper 와 같다). to 를 null 로 두어도 원래 도착 위치로 간다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.math.Vector3d;
import org.domi.furnace.api.world.Location;

public final class EntityPortalExitEvent extends EntityEvent implements Cancellable {

    private final Location from;
    private Location to;
    private final Vector3d before;
    private Vector3d after;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityPortalExitEvent(Entity entity, Location from, Location to, Vector3d before, Vector3d after) {
        super(entity);
        this.from = from;
        this.to = to;
        this.before = before;
        this.after = after;
    }

    public Location from() { return from; }

    /** 도착 위치. */
    public Location to() { return to; }

    public void to(Location to) { this.to = to; }

    /** 포털에 들어가기 전(지금) 속도. */
    public Vector3d before() { return before; }

    /** 도착한 뒤 속도. */
    public Vector3d after() { return after; }

    public void after(Vector3d after) { this.after = after; }
}
