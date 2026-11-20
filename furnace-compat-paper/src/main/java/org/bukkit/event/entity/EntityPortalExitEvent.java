package org.bukkit.event.entity;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.HandlerList;
import org.bukkit.util.Vector;

/**
 * 플레이어가 아닌 엔티티가 차원문을 지나 도착 위치로 옮겨지기 직전(Paper Entity#changeDimension — EntityTeleportEvent 뒤). 도착 위치와 도착 속도를
 * 바꿀 수 있다. 취소하면 이동은 그대로 일어나고 이 이벤트에서 바꾼 값만 버린다.
 */
public class EntityPortalExitEvent extends EntityTeleportEvent {

    private static final HandlerList handlers = new HandlerList();

    @Override
    public HandlerList getHandlers() { return handlers; }

    public static HandlerList getHandlerList() { return handlers; }

    private Vector before;
    private Vector after;

    public EntityPortalExitEvent(Entity entity, Location from, Location to, Vector before, Vector after) {
        super(entity, from, to);
        this.before = before;
        this.after = after;
    }

    /** 포털에 들어가기 전 속도(복사본). */
    public Vector getBefore() { return before.clone(); }

    /** 도착한 뒤 속도(복사본). */
    public Vector getAfter() { return after.clone(); }

    public void setAfter(Vector after) { this.after = after.clone(); }
}
