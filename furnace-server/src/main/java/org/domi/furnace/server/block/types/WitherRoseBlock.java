// 바닐라 WitherRoseBlock: 위더 장미. 네더랙·영혼 모래·영혼 흙 위에서도 살고, 닿은 생물에게 시듦(40틱)을 건다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class WitherRoseBlock extends FlowerBlock {

    private static final int NETHERRACK = B.id("netherrack"), SOUL_SAND = B.id("soul_sand"), SOUL_SOIL = B.id("soul_soil");

    public WitherRoseBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return super.mayPlaceOn(state, level, pos) || B.is(state, NETHERRACK) || B.is(state, SOUL_SAND) || B.is(state, SOUL_SOIL);
    }

    // TODO(엔티티): entityInside — 평화가 아니면 시듦에 면역이 아닌 생물에게 WITHER 40틱.
}
