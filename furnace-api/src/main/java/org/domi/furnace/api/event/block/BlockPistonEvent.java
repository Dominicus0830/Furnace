// 피스톤이 움직이기 직전 이벤트의 바탕 — 피스톤의 region 스레드에서 불린다. pos 는 피스톤 칸, direction 은 피스톤이 바라보는 쪽.
package org.domi.furnace.api.event.block;

import java.util.List;
import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockPos;

public abstract class BlockPistonEvent extends BlockEvent implements Cancellable {

    private final String direction;
    private final boolean sticky;
    private final List<BlockPos> blocks;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    protected BlockPistonEvent(String world, BlockPos pos, String direction, boolean sticky, List<BlockPos> blocks) {
        super(world, pos);
        this.direction = direction;
        this.sticky = sticky;
        this.blocks = List.copyOf(blocks);
    }

    /** 피스톤이 바라보는 쪽(down, up, north, south, west, east). */
    public String direction() { return direction; }

    public boolean isSticky() { return sticky; }

    /** 밀리거나(끌리거나) 부서질 블록 칸들 — 바꿀 수 없다. */
    public List<BlockPos> blocks() { return blocks; }
}
