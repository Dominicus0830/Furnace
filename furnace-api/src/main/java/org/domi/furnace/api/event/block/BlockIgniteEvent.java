// 칸에 불이 붙기 직전 — 그 칸의 region 스레드에서 불린다. 취소하면 불이 생기지 않는다(부싯돌은 그래도 닳고, 화염구는 그래도 쓴다).
// 이미 불인 칸으로 번질 때는 부르지 않는다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockIgniteEvent extends BlockEvent implements Cancellable {

    public enum Cause {
        /** 불이 번짐(ignitingPos = 번지는 불). */
        SPREAD,
        /** 용암 무작위 틱(ignitingPos = 용암). */
        LAVA,
        /** 부싯돌과 부시(player). */
        FLINT_AND_STEEL,
        /** 화염구 아이템을 블록에 씀(player). */
        FIREBALL
    }

    private final Cause cause;
    private final BlockPos ignitingPos;
    private final Player player;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockIgniteEvent(String world, BlockPos pos, Cause cause, BlockPos ignitingPos, Player player) {
        super(world, pos);
        this.cause = cause;
        this.ignitingPos = ignitingPos;
        this.player = player;
    }

    public Cause cause() { return cause; }

    /** 불을 붙인 블록 칸(없으면 null). */
    public BlockPos ignitingPos() { return ignitingPos; }

    /** 불을 붙인 플레이어(없으면 null). */
    public Player player() { return player; }
}
