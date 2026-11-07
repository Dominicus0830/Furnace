// 플레이어가 블록을 부수기 직전 — 그 블록을 소유한 region 스레드에서 불린다(그 자리에서 월드를 안전하게 볼 수 있다). 취소하면 블록이 돌아온다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockBreakEvent extends BlockEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private final Player player;
    private final String blockType;
    private boolean cancelled;

    public BlockBreakEvent(String world, BlockPos pos, String blockType, Player player) {
        super(world, pos);
        this.blockType = blockType;
        this.player = player;
    }

    public Player player() { return player; }

    /** [부순 플레이어 break]. */
    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() { return player == null ? org.domi.furnace.api.event.cause.CauseChain.NONE : org.domi.furnace.api.event.cause.CauseChain.of(player, org.domi.furnace.api.event.cause.CauseEntry.BREAK); }

    /** 부서질 블록(minecraft:stone 등). */
    public String blockType() { return blockType; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
