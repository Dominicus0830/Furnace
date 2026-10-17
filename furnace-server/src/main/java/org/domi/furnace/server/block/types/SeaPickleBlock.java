// 바닐라 SeaPickleBlock: 바다 피클. 같은 것을 들고 누르면 1→4 개로 늘고, 산호 블록 위 물속에서 뼛가루를 쓰면 둘레에 번진다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class SeaPickleBlock extends BushBlock implements BonemealableBlock {

    static final Prop PICKLES = B.prop("pickles");
    private static final int WATER = B.id("water"), SEA_PICKLE = B.id("sea_pickle");

    public SeaPickleBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos());
        if (B.is(s, block)) return PICKLES.withInt(s, Math.min(4, PICKLES.intValue(s) + 1));
        return WATERLOGGED.with(super.getStateForPlacement(ctx), B.waterAt(ctx));
    }

    /** isDead: 물에 잠기지 않았다. */
    public static boolean isDead(int state) { return !WATERLOGGED.is(state); }

    @Override
    protected boolean mayPlaceOn(int state, BlockLevel level, Pos pos) {
        return !B.behavior(state).getCollisionShape(state, level, pos, null).isFaceEmpty(Dir.UP) || B.isFaceSturdy(state, level, pos, Dir.UP);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos below = pos.below();
        return mayPlaceOn(level.getBlockState(below), level, below);
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!canSurvive(state, level, pos)) return B.defaultState("air");
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canBeReplaced(int state, PlaceContext ctx) {
        return !ctx.isSecondaryUseActive() && ctx.holding(block) && PICKLES.intValue(state) < 4 || super.canBeReplaced(state, ctx);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return true; }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        if (isDead(state) || !B.isTag(level.getBlockState(pos.below()), "minecraft:coral_blocks")) return;
        int width = 1, step = 0, x0 = pos.x() - 2, zOff = 0;
        for (int dx = 0; dx < 5; dx++) {
            for (int dz = 0; dz < width; dz++) {
                int top = 2 + pos.y() - 1;
                for (int y = top - 2; y < top; y++) {
                    Pos p = new Pos(x0 + dx, y, pos.z() - zOff + dz);
                    // 바닐라의 p != pos 는 새 객체끼리의 참조 비교라 늘 참이다.
                    if (random.nextInt(6) == 0 && B.is(level.getBlockState(p), WATER)
                            && B.isTag(level.getBlockState(p.below()), "minecraft:coral_blocks")) {
                        level.setBlock(p, PICKLES.withInt(B.defaultState(SEA_PICKLE), random.nextInt(4) + 1), 3);
                    }
                }
            }
            if (step < 2) {
                width += 2;
                zOff++;
            } else {
                width -= 2;
                zOff--;
            }
            step++;
        }
        level.setBlock(pos, PICKLES.withInt(state, 4), 2);
    }
}
