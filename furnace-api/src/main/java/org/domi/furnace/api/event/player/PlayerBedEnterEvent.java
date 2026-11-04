// 침대에서 잠들기 직전(밤·안전 등 바닐라 검사를 모두 통과한 뒤) — 침대가 있는 region 스레드에서 불린다. 취소하면 잠들지 않는다(리스폰 지점은 이미 정해졌다, 바닐라 순서).
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.Block;

public final class PlayerBedEnterEvent extends PlayerEvent implements Cancellable {

    private final Block bed;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public PlayerBedEnterEvent(Player player, Block bed) {
        super(player);
        this.bed = bed;
    }

    /** 침대 머리 칸. */
    public Block bed() { return bed; }
}
