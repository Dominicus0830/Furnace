// 바닐라 IceBlock: 섬세한 손길 없이 부수면 아래가 막혀 있거나 액체일 때 물이 된다(뜨거운 차원은 그냥 사라짐). 밝으면 녹는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;

public class IceBlock extends HalfTransparentBlock {

    private static final int WATER = B.id("water");
    /** 얼음의 getLightBlock(빛을 1 줄인다 — 꽉 찬 모양이지만 가리지 않는 블록). */
    static final int LIGHT_BLOCK = 1;

    public IceBlock(int block) { super(block); }

    public static int meltsInto() { return B.defaultState(WATER); }

    @Override
    public void playerDestroy(BlockLevel level, BlockActor player, Pos pos, int state, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, tool);
        // TODO(3-4b): EnchantmentHelper.hasTag(tool, #prevents_ice_melting)(섬세한 손길)이면 녹지 않는다.
        if (level.ultraWarm()) {
            level.removeBlock(pos, false);
            return;
        }
        int below = level.getBlockState(pos.below());
        if (INFO.blocksMotion(below) || B.isLiquid(below)) level.setBlockAndUpdate(pos, meltsInto());
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getBlockLight(pos) > 11 - LIGHT_BLOCK) melt(state, level, pos);
    }

    /** melt: 뜨거운 차원이면 사라지고, 아니면 물이 되어 이웃에 알린다. */
    protected void melt(int state, BlockLevel level, Pos pos) {
        if (level.ultraWarm()) {
            level.removeBlock(pos, false);
        } else {
            level.setBlockAndUpdate(pos, meltsInto());
            level.neighborChanged(pos, WATER, pos);
        }
    }
}
