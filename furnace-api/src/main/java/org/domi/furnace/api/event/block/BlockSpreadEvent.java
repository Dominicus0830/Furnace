// 블록이 옆 칸으로 번지기 직전에 그 블록의 region 스레드에서 불린다. 풀과 균사체가 흙으로 번지는 경우, 버섯, 덩굴, 불이 해당한다.
// pos() 는 새로 생길 칸이고 source() 는 번지는 쪽 칸이다. 취소하면 번지지 않는다.
package org.domi.furnace.api.event.block;

import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;

public final class BlockSpreadEvent extends BlockFormEvent {

    private final BlockPos source;

    public BlockSpreadEvent(String world, BlockPos pos, BlockPos source, BlockData oldData, BlockData newData) {
        super(world, pos, oldData, newData);
        this.source = source;
    }

    public BlockPos source() { return source; }
}
