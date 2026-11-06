// 블록이 저절로 사라지거나 바뀌기 직전 — 얼음·눈 녹음, 불 꺼짐, 산호 죽음, 풀이 흙으로, 마른 경작지가 흙으로. 그 블록의 region 스레드에서 불린다.
// 취소하면 그 칸은 그대로다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockFadeEvent extends BlockEvent implements Cancellable {

    private final BlockData oldData;
    private final BlockData newData;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockFadeEvent(String world, BlockPos pos, BlockData oldData, BlockData newData) {
        super(world, pos);
        this.oldData = oldData;
        this.newData = newData;
    }

    public BlockData oldData() { return oldData; }

    /** 바뀔 블록(사라지면 공기). */
    public BlockData newData() { return newData; }
}
