// 블록이 저절로 만들어지기 직전 — 그 블록의 region 스레드에서 불린다. 눈 쌓임·물 얼음·용암+물(흑요석·조약돌·현무암)·콘크리트 가루 굳기가 해당한다.
// 취소하면 그 칸은 그대로다. 번짐(풀·균사체·버섯·덩굴·불)은 하위 BlockSpreadEvent.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.event.Cancellable;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public class BlockFormEvent extends BlockEvent implements Cancellable {

    private final BlockData oldData;
    private final BlockData newData;
    private boolean cancelled;

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }

    public BlockFormEvent(String world, BlockPos pos, BlockData oldData, BlockData newData) {
        super(world, pos);
        this.oldData = oldData;
        this.newData = newData;
    }

    /** 지금 그 칸의 블록. */
    public BlockData oldData() { return oldData; }

    /** 바뀔 블록. */
    public BlockData newData() { return newData; }
}
