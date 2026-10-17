// 바닐라 CocoaBlock: 정글 나무 옆에 붙는 코코아. 무작위 틱(레벨 난수 1/5)마다 나이 0~2 로 자란다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class CocoaBlock extends HorizontalDirectionalBlock implements BonemealableBlock {

    private static final Prop AGE = B.prop("age");

    public CocoaBlock(int block) { super(block); }

    private static int facing(int state) { return Dir.byName(FACING.valueName(state)); }

    @Override
    public boolean isRandomlyTicking(int state) { return AGE.intValue(state) < 2; }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.random().nextInt(5) == 0) {   // 바닐라는 인자가 아니라 레벨 난수를 쓴다
            int age = AGE.intValue(state);
            if (age < 2) level.setBlock(pos, AGE.withInt(state, age + 1), 2);
        }
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return B.isTag(level.getBlockState(pos.relative(facing(state))), "minecraft:jungle_logs");
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = defaultState();
        for (int d : ctx.nearestLookingDirections()) {
            if (Dir.horizontal(d)) {
                s = FACING.with(s, Dir.NAME[d]);
                if (B.canSurvive(s, ctx.level(), ctx.clickedPos())) return s;
            }
        }
        return -1;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        return dir == facing(state) && !canSurvive(state, level, pos) ? B.defaultState("air") : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return AGE.intValue(state) < 2; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        level.setBlock(pos, AGE.withInt(state, AGE.intValue(state) + 1), 2);
    }
}
