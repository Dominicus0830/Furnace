// 나무에서 떨어진 잎이 무작위 틱으로 사라지기 직전 — 그 블록의 region 스레드에서 불린다. 취소하면 사라지지 않는다(드롭도 없다).
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class LeavesDecayEvent extends BlockEvent implements Cancellable {

    private final BlockData block;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public LeavesDecayEvent(String world, BlockPos pos, BlockData block) {
        super(world, pos);
        this.block = block;
    }

    public BlockData block() { return block; }
}
