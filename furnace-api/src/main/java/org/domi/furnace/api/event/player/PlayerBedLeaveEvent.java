// 잠든 플레이어가 침대에서 일어날 때(아침, 나가기 버튼, 침대가 부서짐 등) — 플레이어를 가진 region 스레드에서 불린다.
package org.domi.furnace.api.event.player;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.world.BlockPos;

public final class PlayerBedLeaveEvent extends PlayerEvent {

    private final String world;
    private final BlockPos bed;

    public PlayerBedLeaveEvent(Player player, String world, BlockPos bed) {
        super(player);
        this.world = world;
        this.bed = bed;
    }

    public String world() { return world; }

    /** 자고 있던 침대 칸(모르면 null). */
    public BlockPos bed() { return bed; }
}
