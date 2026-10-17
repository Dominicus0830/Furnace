// 바닐라 PistonBaseBlock: 피스톤·끈끈이 피스톤. 바라보는 방향의 반대로 놓인다.
// 밀고 당기기(checkIfExtend → 블록 이벤트 → triggerEvent → 움직이는 피스톤 블록 엔티티)는 TODO(3-4e 레드스톤·블록엔티티):
// 움직이는 블록 엔티티가 2틱 뒤 마무리해야 하므로, 그 체계 없이 절반만 옮기면 움직이는 피스톤 블록이 남는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Prop;

public class PistonBaseBlock extends DirectionalBlock {

    static final Prop EXTENDED = B.prop("extended");

    public PistonBaseBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return EXTENDED.with(withFacing(defaultState(), Dir.opposite(ctx.nearestLookingDirection())), false);
    }
}
