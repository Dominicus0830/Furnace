// 플레이어가 차원문(지옥문·엔드 포털·엔드 관문)에 충분히 머물러 다른 곳으로 넘어가기 직전 — 플레이어의 region 스레드에서 불린다.
// 목적지 좌표는 목적 차원 쪽에서 청크를 올린 뒤 정해지므로 이 이벤트에는 없고, 목적 차원(toWorld)만 알려 준다(엔드 포털로 엔드에서 돌아올 때는 리스폰 위치가 따로 정해진다).
// 취소하면 넘어가지 않는다(차원문 대기 시간은 이미 쓴 상태).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Location;

public final class PlayerPortalEvent extends PlayerEvent implements Cancellable {

    public enum Cause { NETHER_PORTAL, END_PORTAL, END_GATEWAY }

    private final Location from;
    private final String toWorld;
    private final Cause cause;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerPortalEvent(Player player, Location from, String toWorld, Cause cause) {
        super(player);
        this.from = from;
        this.toWorld = toWorld;
        this.cause = cause;
    }

    public Location from() { return from; }

    /** 가려는 차원 키(엔드 관문은 같은 차원). */
    public String toWorld() { return toWorld; }

    public Cause cause() { return cause; }
}
