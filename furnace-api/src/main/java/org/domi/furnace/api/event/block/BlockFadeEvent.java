// 블록이 저절로 사라지거나 바뀌기 직전에 그 블록의 region 스레드에서 불린다.
// 얼음과 눈이 녹는 경우, 불이 꺼지는 경우, 산호가 죽는 경우, 풀이 흙으로 바뀌는 경우, 마른 경작지가 흙으로 바뀌는 경우가 해당한다.
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
