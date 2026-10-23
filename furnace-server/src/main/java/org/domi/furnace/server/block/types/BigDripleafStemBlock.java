// 바닐라 BigDripleafStemBlock: 큰 흘림잎 줄기. 아래(줄기·놓을 수 있는 땅)와 위(줄기·잎)가 이어져야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class BigDripleafStemBlock extends HorizontalDirectionalBlock implements BonemealableBlock {

    public BigDripleafStemBlock(int block) { super(block); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int below = level.getBlockState(pos.below()), above = level.getBlockState(pos.above());
        return (B.is(below, block) || B.isTag(below, "minecraft:big_dripleaf_placeable"))
                && (B.is(above, block) || B.is(above, BigDripleafBlock.BIG_DRIPLEAF));
    }

    static boolean place(BlockLevel level, Pos pos, int fluid, int facing) {
        int s = WATERLOGGED.with(B.defaultState(BigDripleafBlock.BIG_DRIPLEAF_STEM), Fluids.isWaterSource(fluid));
        return level.setBlock(pos, FACING.with(s, Dir.NAME[facing]), 3);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if ((dir == Dir.DOWN || dir == Dir.UP) && !canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    /** BlockUtil.getTopConnectedBlock(level, pos, 줄기, 위, 잎): 줄기를 따라 올라가 잎이면 그 칸. */
    private Pos topLeaf(BlockLevel level, Pos pos) {
        Pos m = pos;
        int s;
        do {
            m = m.above();
            s = level.getBlockState(m);
        } while (B.is(s, block));
        return B.is(s, BigDripleafBlock.BIG_DRIPLEAF) ? m : null;
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) {
        Pos top = topLeaf(level, pos);
        if (top == null) return false;
        Pos above = top.above();
        return BigDripleafBlock.canPlaceAt(level, above, level.getBlockState(above));
    }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        Pos top = topLeaf(level, pos);
        if (top != null) {
            Pos above = top.above();
            int facing = Dir.byName(FACING.valueName(state));
            place(level, top, level.getFluidState(top), facing);
            BigDripleafBlock.place(level, above, level.getFluidState(above), facing);
        }
    }
}
