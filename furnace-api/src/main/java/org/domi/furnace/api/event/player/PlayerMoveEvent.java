// 플레이어가 움직인 직후(서버 검증 통과 뒤) — 플레이어를 가진 region 스레드에서 불린다. 취소하면 from 으로 되돌린다(보정 텔레포트).
// CraftBukkit/Paper 규칙: 마지막으로 알린 자리에서 거리² > 1/256 이거나 (|Δyaw| + |Δpitch|) > 10° 일 때만 부른다 — 시선만 크게 돌려도 불리고,
// 작은 떨림은 불리지 않는다. from 은 "마지막으로 알린 자리"(위치·회전)이고 텔레포트 직후에는 그 자리부터 다시 센다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Location;

public final class PlayerMoveEvent extends PlayerEvent implements Cancellable {

    private final Location from, to;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerMoveEvent(Player player, Location from, Location to) {
        super(player);
        this.from = from;
        this.to = to;
    }

    public Location from() { return from; }

    public Location to() { return to; }

    /** 위치가 (같은 칸 안에서라도) 바뀌었는가. 회전만 바뀐 이벤트면 false. */
    public boolean hasChangedPosition() { return from.x() != to.x() || from.y() != to.y() || from.z() != to.z(); }

    /** 시선(yaw·pitch)이 바뀌었는가. */
    public boolean hasChangedOrientation() { return from.yaw() != to.yaw() || from.pitch() != to.pitch(); }

    public boolean hasChangedBlock() { return from.blockX() != to.blockX() || from.blockY() != to.blockY() || from.blockZ() != to.blockZ(); }
}
