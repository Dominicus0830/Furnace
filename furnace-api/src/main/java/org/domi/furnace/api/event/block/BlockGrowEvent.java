// 작물·줄기·사탕수수·선인장·네더 사마귀·코코아·달콤한 열매 덤불이 자라기 직전(무작위 틱, 뼛가루) — 그 블록의 region 스레드에서 불린다.
// pos 는 바뀔 칸(사탕수수·선인장·박 열매는 새로 생길 칸). 취소하면 자라지 않는다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockGrowEvent extends BlockEvent implements Cancellable {

    private final BlockData oldData;
    private final BlockData newData;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockGrowEvent(String world, BlockPos pos, BlockData oldData, BlockData newData) {
        super(world, pos);
        this.oldData = oldData;
        this.newData = newData;
    }

    public BlockData oldData() { return oldData; }

    public BlockData newData() { return newData; }
}
