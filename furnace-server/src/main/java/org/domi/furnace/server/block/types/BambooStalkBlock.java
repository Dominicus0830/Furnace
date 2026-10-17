// 바닐라 BambooStalkBlock: 대나무 줄기. 위로 자라며 꼭대기 쪽 잎(none/small/large)을 옮기고, 높이 16 에서 멈춘다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class BambooStalkBlock extends BlockBehavior implements BonemealableBlock {

    static final Prop AGE = B.prop("age"), LEAVES = B.prop("leaves"), STAGE = B.prop("stage");
    private static final int BAMBOO = B.id("bamboo"), BAMBOO_SAPLING = B.id("bamboo_sapling");
    private static final String NONE = "none", SMALL = "small", LARGE = "large";

    public BambooStalkBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        if (!Fluids.isEmpty(level.getFluidState(pos))) return -1;
        int below = level.getBlockState(pos.below());
        if (!B.isTag(below, "minecraft:bamboo_plantable_on")) return -1;
        if (B.is(below, BAMBOO_SAPLING)) return AGE.withInt(defaultState(), 0);
        if (B.is(below, BAMBOO)) return AGE.withInt(defaultState(), AGE.intValue(below) > 0 ? 1 : 0);
        int above = level.getBlockState(pos.above());
        return B.is(above, BAMBOO) ? AGE.withInt(defaultState(), AGE.intValue(above)) : B.defaultState(BAMBOO_SAPLING);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    @Override
    public boolean isRandomlyTicking(int state) { return STAGE.intValue(state) == 0; }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (STAGE.intValue(state) != 0) return;
        if (random.nextInt(3) == 0 && level.isEmptyBlock(pos.above()) && level.getRawBrightness(pos.above(), 0) >= 9) {
            int height = heightBelow(level, pos) + 1;
            if (height < 16) growBamboo(state, level, pos, random, height);
        }
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        return B.isTag(level.getBlockState(pos.below()), "minecraft:bamboo_plantable_on");
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (!canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
        if (dir == Dir.UP && B.is(neighborState, BAMBOO) && AGE.intValue(neighborState) > AGE.intValue(state)) {
            level.setBlock(pos, AGE.cycle(state), 2);
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) {
        int above = heightAbove(level, pos), below = heightBelow(level, pos);
        return above + below + 1 < 16 && STAGE.intValue(level.getBlockState(pos.above(above))) != 1;
    }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        int above = heightAbove(level, pos), below = heightBelow(level, pos);
        int height = above + below + 1;
        int n = 1 + random.nextInt(2);
        for (int i = 0; i < n; i++) {
            Pos top = pos.above(above);
            int ts = level.getBlockState(top);
            if (height >= 16 || STAGE.intValue(ts) == 1 || !level.isEmptyBlock(top.above())) return;
            growBamboo(ts, level, top, random, height);
            above++;
            height++;
        }
    }

    // TODO(3-4b): getDestroyProgress — 칼을 들면 1.0(한 번에 부서짐).

    protected void growBamboo(int state, BlockLevel level, Pos pos, RandomSource random, int height) {
        int below = level.getBlockState(pos.below());
        Pos pos2 = pos.below(2);
        int s2 = level.getBlockState(pos2);
        String leaves = NONE;
        if (height >= 1) {
            if (!B.is(below, BAMBOO) || NONE.equals(LEAVES.valueName(below))) {
                leaves = SMALL;
            } else if (B.is(below, BAMBOO) && !NONE.equals(LEAVES.valueName(below))) {
                leaves = LARGE;
                if (B.is(s2, BAMBOO)) {
                    level.setBlock(pos.below(), LEAVES.with(below, SMALL), 3);
                    level.setBlock(pos2, LEAVES.with(s2, NONE), 3);
                }
            }
        }
        int age = AGE.intValue(state) != 1 && !B.is(s2, BAMBOO) ? 0 : 1;
        int stage = (height < 11 || !(random.nextFloat() < 0.25F)) && height != 15 ? 0 : 1;
        level.setBlock(pos.above(), STAGE.withInt(LEAVES.with(AGE.withInt(defaultState(), age), leaves), stage), 3);
    }

    /** getHeightAboveUpToMax: 위로 이어진 대나무 수(16 까지). */
    protected int heightAbove(BlockLevel level, Pos pos) {
        int i = 0;
        while (i < 16 && B.is(level.getBlockState(pos.above(i + 1)), BAMBOO)) i++;
        return i;
    }

    /** getHeightBelowUpToMax. */
    protected int heightBelow(BlockLevel level, Pos pos) {
        int i = 0;
        while (i < 16 && B.is(level.getBlockState(pos.below(i + 1)), BAMBOO)) i++;
        return i;
    }
}
