// 피스톤이 늘어나기 직전. 취소하면 늘어나지 않는다(블록도 밀리지 않는다).
package org.domi.furnace.api.event.block;

import java.util.List;
import org.domi.furnace.api.world.BlockPos;

public final class BlockPistonExtendEvent extends BlockPistonEvent {

    public BlockPistonExtendEvent(String world, BlockPos pos, String direction, boolean sticky, List<BlockPos> blocks) {
        super(world, pos, direction, sticky, blocks);
    }
}
