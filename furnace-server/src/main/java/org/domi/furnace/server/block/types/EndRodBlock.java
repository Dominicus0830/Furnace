// 바닐라 EndRodBlock: 엔드 막대. 클릭한 면 쪽을 보되, 같은 쪽을 보는 막대에 이어 붙이면 반대로 뒤집는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;

public class EndRodBlock extends DirectionalBlock {

    public EndRodBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int face = ctx.clickedFace();
        int behind = ctx.level().getBlockState(ctx.clickedPos().relative(Dir.opposite(face)));
        return B.is(behind, block) && facing(behind) == face ? withFacing(defaultState(), Dir.opposite(face)) : withFacing(defaultState(), face);
    }
}
