// 바닐라 BushBlock: 흙류(#dirt)나 경작지 위에서만 산다. 살 수 없게 되면 모양 갱신에서 공기로.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public abstract class BushBlock extends BlockBehavior {

    private static final int FARMLAND = B.id("farmland");

    protected BushBlock(int block) { super(block); }

    /** mayPlaceOn(state, level, pos) — 아래 칸 상태. */
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return B.isTag(state, "minecraft:dirt") || B.is(state, FARMLAND);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }
}
