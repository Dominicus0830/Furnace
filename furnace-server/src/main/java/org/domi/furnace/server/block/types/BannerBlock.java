// 바닐라 BannerBlock: 서 있는 깃발(rotation 0..15). 아래가 단단한(legacySolid) 블록이어야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class BannerBlock extends AbstractBannerBlock {

    static final Prop ROTATION = B.prop("rotation");

    public BannerBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return B.isSolid(level.getBlockState(pos.below())); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return ROTATION.withInt(defaultState(), SignBlock.segment(ctx.rotation() + 180.0F)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == Dir.DOWN && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }
}
