// 바닐라 SoulFireBlock: 영혼 흙·영혼 모래(#soul_fire_base_blocks) 위에서만 사는 불. 번지지 않는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class SoulFireBlock extends BaseFireBlock {

    public SoulFireBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return canSurvive(state, level, pos) ? defaultState() : B.defaultState("air");
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return canSurviveOnBlock(level.getBlockState(pos.below())); }

    public static boolean canSurviveOnBlock(int state) { return B.isTag(state, "minecraft:soul_fire_base_blocks"); }

    @Override
    protected boolean canBurn(int state) { return true; }
}
