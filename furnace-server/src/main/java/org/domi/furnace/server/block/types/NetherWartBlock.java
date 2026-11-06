// 바닐라 NetherWartBlock: 네더 사마귀. 영혼 모래 위에서만 살고, 무작위 틱마다 1/10 확률로 자란다(age 0..3).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class NetherWartBlock extends BushBlock {

    static final Prop AGE = B.prop("age");
    private static final int SOUL_SAND = B.id("soul_sand");

    public NetherWartBlock(int block) { super(block); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) { return B.is(state, SOUL_SAND); }

    @Override
    public boolean isRandomlyTicking(int state) { return AGE.intValue(state) < 3; }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        int age = AGE.intValue(state);
        if (age < 3 && random.nextInt(10) == 0) B.grow(level, pos, AGE.withInt(state, age + 1), 2);
    }
}
