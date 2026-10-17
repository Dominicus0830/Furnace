// 바닐라 GlowLichenBlock: 발광 이끼. 다면 블록 + 물에 잠김 + 뼛가루로 무작위 면에서 무작위 방향으로 번진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class GlowLichenBlock extends MultifaceBlock implements BonemealableBlock {

    private final MultifaceSpreader spreader = new MultifaceSpreader(this);

    public GlowLichenBlock(int block) { super(block); }

    @Override
    public MultifaceSpreader getSpreader() { return spreader; }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) { return !ctx.holding(block) || super.canBeReplaced(state, ctx); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) {
        for (int d : Dir.ALL) if (spreader.canSpreadInAnyDirection(state, level, pos, Dir.opposite(d))) return true;
        return false;
    }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        spreader.spreadFromRandomFaceTowardRandomDirection(state, level, pos, random);
    }
}
