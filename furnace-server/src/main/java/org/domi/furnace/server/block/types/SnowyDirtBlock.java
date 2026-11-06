// 바닐라 SnowyDirtBlock: 눈 덮일 수 있는 흙(회백토·잔디·균사체 바탕). 위가 #snow 면 snowy.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class SnowyDirtBlock extends BlockBehavior {

    static final Prop SNOWY = B.prop("snowy");

    public SnowyDirtBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.UP ? SNOWY.with(state, isSnowySetting(neighborState)) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return SNOWY.with(defaultState(), isSnowySetting(ctx.level().getBlockState(ctx.clickedPos().above())));
    }

    static boolean isSnowySetting(int state) { return B.isTag(state, "minecraft:snow"); }
}
