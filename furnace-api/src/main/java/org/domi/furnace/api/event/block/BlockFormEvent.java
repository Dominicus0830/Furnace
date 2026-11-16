// 블록이 저절로 만들어지기 직전에 그 블록의 region 스레드에서 불린다.
// 눈이 쌓이는 경우, 물이 어는 경우, 용암과 물이 만나 흑요석·조약돌·현무암이 되는 경우, 콘크리트 가루가 굳는 경우가 해당한다.
// 취소하면 그 칸은 그대로다. 풀·균사체·버섯·덩굴·불이 번지는 경우는 하위 이벤트 BlockSpreadEvent 가 맡는다.
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
