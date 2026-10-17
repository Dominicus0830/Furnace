// 바닐라 BarrierBlock: 보이지 않는 벽. 물에 잠길 수 있지만 크리에이티브 플레이어만 물을 뜨거나 넣는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class BarrierBlock extends BlockBehavior {

    public BarrierBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return WATERLOGGED.with(defaultState(), B.waterAt(ctx)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    // TODO(아이템): pickupBlock·canPlaceLiquid 은 크리에이티브 플레이어일 때만(양동이 아이템 동작이 BucketPickup 을 부를 때).
}
