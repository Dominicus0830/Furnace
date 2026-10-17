// 바닐라 GrassBlock: 잔디 블록. 퍼지는 흙 + 뼛가루(위가 공기면 둘레에 풀·꽃).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public class GrassBlock extends SpreadingSnowyDirtBlock implements BonemealableBlock {

    public GrassBlock(int block) { super(block); }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return B.isAir(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        // TODO(지형 생성 연결): 위 칸에서 128 번 무작위 걸음(i/16 걸음, 걸음마다 nextInt(3)·nextInt(3)·nextInt(3)·nextInt(3)),
        //   풀이면 1/10 로 그 풀에 뼛가루, 공기면 1/8 로 바이옴의 첫 꽃 feature, 아니면 placed feature grass_bonemeal 을 놓는다.
    }
}
