// 바닐라 EndPortalFrameBlock: 엔드 차원문 틀. 바라보는 쪽을 향해 놓이고, 엔더의 눈이 끼면 비교기 신호 15.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class EndPortalFrameBlock extends BlockBehavior {

    static final Prop FACING = B.prop("facing"), EYE = B.prop("eye");

    public EndPortalFrameBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return EYE.with(FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]), false);
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return EYE.is(state) ? 15 : 0; }

    // TODO(아이템): getOrCreatePortalShape — 엔더의 눈(EnderEyeItem)이 12 개 틀을 다 채웠는지 보는 5×5 무늬. 아이템 쪽에서 쓴다.
}
