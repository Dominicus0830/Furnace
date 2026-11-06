// 바닐라 MangroveLeavesBlock: 맹그로브 잎. 아래가 비었으면 뼛가루로 매달린 맹그로브 싹을 단다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class MangroveLeavesBlock extends LeavesBlock implements BonemealableBlock {

    private static final int PROPAGULE = B.id("mangrove_propagule");

    public MangroveLeavesBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.below())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        level.setBlock(pos.below(), B.prop("age").withInt(B.prop("hanging").with(B.defaultState(PROPAGULE), true), 0), 2);
    }
}
