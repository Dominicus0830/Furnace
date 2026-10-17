// 바닐라 MossBlock: 이끼 블록. 위가 공기면 뼛가루로 이끼 무리(moss_patch_bonemeal)를 깐다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class MossBlock extends BlockBehavior implements BonemealableBlock {

    public MossBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(지형 생성 연결): configured feature moss_patch_bonemeal 을 pos.above() 에 놓는다.
    }
}
