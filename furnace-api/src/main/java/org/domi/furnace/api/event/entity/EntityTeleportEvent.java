// 플레이어가 아닌 엔티티를 플러그인이 teleport 로 옮기기 직전에 엔티티의 region 스레드에서 불린다(Paper CraftEntity#teleport 의 EntityTeleportEvent).
// 다른 월드로 가는 이동은 Paper 처럼 두 번 불린다: 처음엔 요청한 위치로, 두 번째는 차원을 건너기 직전(Entity#changeDimension) 도착 위치로.
// 취소하거나 to 를 null 로 두면 옮기지 않는다. to 를 바꾸면(다른 월드로도) 그 위치로 간다.
package org.domi.furnace.api.event.entity;

import org.domi.furnace.api.entity.Entity;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Location;

public final class EntityTeleportEvent extends EntityEvent implements Cancellable {

    private final Location from;
    private Location to;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public EntityTeleportEvent(Entity entity, Location from, Location to) {
        super(entity);
        this.from = from;
        this.to = to;
    }

    public Location from() { return from; }

    /** 도착 위치. null 이면 옮기지 않는다(Paper 와 같다). */
    public Location to() { return to; }

    public void to(Location to) { this.to = to; }
}
