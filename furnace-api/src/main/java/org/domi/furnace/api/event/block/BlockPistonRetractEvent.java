// 피스톤이 줄어들기 직전. 일반 피스톤은 줄어들기 시작 전(blocks 는 빈 목록)에 불리고, 취소하면 줄어들지 않는다.
// 끈끈이 피스톤은 블록을 끌어오기 직전에 불리고, 취소하면 머리는 들어가지만 블록은 끌려오지 않는다.
package org.domi.furnace.api.event.block;

import java.util.List;
import org.domi.furnace.api.world.BlockPos;

public final class BlockPistonRetractEvent extends BlockPistonEvent {

    public BlockPistonRetractEvent(String world, BlockPos pos, String direction, boolean sticky, List<BlockPos> blocks) {
        super(world, pos, direction, sticky, blocks);
    }
}
