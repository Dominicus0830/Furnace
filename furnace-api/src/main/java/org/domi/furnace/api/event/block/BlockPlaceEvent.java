// 플레이어가 블록을 놓기 직전 — 놓을 자리를 소유한 region 스레드에서 불린다. 취소하면 놓지 않는다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public final class BlockPlaceEvent extends BlockEvent implements Cancellable, org.domi.furnace.api.event.cause.Caused {

    private final Player player;
    private final String placedType;
    private final BlockPos against;
    private boolean cancelled;

    public BlockPlaceEvent(String world, BlockPos pos, String placedType, BlockPos against, Player player) {
        super(world, pos);
        this.placedType = placedType;
        this.against = against;
        this.player = player;
    }

    public Player player() { return player; }

    /** [놓은 플레이어 place]. */
    @Override
    public org.domi.furnace.api.event.cause.CauseChain causeChain() { return player == null ? org.domi.furnace.api.event.cause.CauseChain.NONE : org.domi.furnace.api.event.cause.CauseChain.of(player, org.domi.furnace.api.event.cause.CauseEntry.PLACE); }

    /** 놓일 블록(minecraft:oak_planks 등). */
    public String placedType() { return placedType; }

    /** 클릭한(맞닿은) 블록. */
    public BlockPos against() { return against; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
}
