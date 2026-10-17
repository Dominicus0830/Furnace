// 바닐라 GrowingPlantBlock·GrowingPlantHeadBlock·GrowingPlantBodyBlock 의 공통: 한 방향으로 자라는 덩굴(동굴 덩굴·켈프·뒤틀린/눈물 덩굴).
// 머리(age 0~25)와 몸통이 짝. 붙은 쪽(자라는 방향의 반대)이 받쳐야 산다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.RandomSource;

public abstract class GrowingPlantBlock extends BlockBehavior {

    /** 덩굴 종류별 정보: 머리, 몸통, 자라는 방향, 물 틱(켈프), 무작위 틱 자랄 확률. */
    record Kind(int head, int body, int dir, boolean water, double growChance) {}

    static final Kind CAVE_VINES = new Kind(B.id("cave_vines"), B.id("cave_vines_plant"), Dir.DOWN, false, 0.1);
    static final Kind KELP = new Kind(B.id("kelp"), B.id("kelp_plant"), Dir.UP, true, 0.14);
    static final Kind TWISTING = new Kind(B.id("twisting_vines"), B.id("twisting_vines_plant"), Dir.UP, false, 0.1);
    static final Kind WEEPING = new Kind(B.id("weeping_vines"), B.id("weeping_vines_plant"), Dir.DOWN, false, 0.1);

    protected final Kind kind;

    protected GrowingPlantBlock(int block, Kind kind) {
        super(block);
        this.kind = kind;
    }

    protected GrowingPlantHead headBehavior() { return (GrowingPlantHead) B.behavior(B.defaultState(kind.head)); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int s = ctx.level().getBlockState(ctx.clickedPos().relative(kind.dir));
        return !B.is(s, kind.head) && !B.is(s, kind.body) ? stateForPlacement(ctx.level()) : B.defaultState(kind.body);
    }

    /** getStateForPlacement(level): 기본 상태(머리는 나이 무작위). */
    protected int stateForPlacement(BlockLevel level) { return defaultState(); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        Pos attach = pos.relative(Dir.opposite(kind.dir));
        int s = level.getBlockState(attach);
        if (!canAttachTo(s)) return false;
        return B.is(s, kind.head) || B.is(s, kind.body) || B.isFaceSturdy(s, level, attach, kind.dir);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) level.destroyBlock(pos, true);
    }

    protected boolean canAttachTo(int state) { return true; }

    protected void tickWater(BlockLevel level, Pos pos) { if (kind.water) level.scheduleFluidTick(pos, Fluids.T_WATER, 5); }

    /** 덩굴 머리 쪽 동작(바닐라 GrowingPlantHeadBlock). */
    public abstract static class GrowingPlantHead extends GrowingPlantBlock implements BonemealableBlock {

        protected static final org.domi.furnace.server.block.Prop AGE = B.prop("age");

        protected GrowingPlantHead(int block, Kind kind) { super(block, kind); }

        @Override
        protected int stateForPlacement(BlockLevel level) { return AGE.withInt(defaultState(), level.random().nextInt(25)); }

        @Override
        public boolean isRandomlyTicking(int state) { return AGE.intValue(state) < 25; }

        @Override
        public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
            if (AGE.intValue(state) < 25 && random.nextDouble() < kind.growChance) {
                Pos target = pos.relative(kind.dir);
                if (canGrowInto(level.getBlockState(target))) level.setBlockAndUpdate(target, growIntoState(state, level.random()));
            }
        }

        /** getGrowIntoState: 나이 +1(동굴 덩굴은 열매도 정한다). */
        protected int growIntoState(int state, RandomSource random) { return AGE.cycle(state); }

        public int maxAgeState(int state) { return AGE.withInt(state, 25); }

        public boolean isMaxAge(int state) { return AGE.intValue(state) == 25; }

        /** updateBodyAfterConvertedFromHead: 머리가 몸통이 될 때 옮길 속성. */
        protected int bodyFromHead(int head, int body) { return body; }

        @Override
        public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
            if (dir == Dir.opposite(kind.dir) && !canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
            if (dir != kind.dir || !B.is(neighborState, block) && !B.is(neighborState, kind.body)) {
                tickWater(level, pos);
                return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
            }
            return bodyFromHead(state, B.defaultState(kind.body));
        }

        protected abstract boolean canGrowInto(int state);

        /** getBlocksToGrowWhenBonemealed. */
        protected int blocksToGrowWhenBonemealed(RandomSource random) { return 1; }

        @Override
        public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return canGrowInto(level.getBlockState(pos.relative(kind.dir))); }

        @Override
        public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

        @Override
        public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
            Pos target = pos.relative(kind.dir);
            int age = Math.min(AGE.intValue(state) + 1, 25);
            int n = blocksToGrowWhenBonemealed(random);
            for (int i = 0; i < n && canGrowInto(level.getBlockState(target)); i++) {
                level.setBlockAndUpdate(target, AGE.withInt(state, age));
                target = target.relative(kind.dir);
                age = Math.min(age + 1, 25);
            }
        }
    }

    /** 덩굴 몸통 쪽 동작(바닐라 GrowingPlantBodyBlock). */
    public abstract static class GrowingPlantBody extends GrowingPlantBlock implements BonemealableBlock {

        protected GrowingPlantBody(int block, Kind kind) { super(block, kind); }

        /** updateHeadAfterConvertedFromBody. */
        protected int headFromBody(int body, int head) { return head; }

        @Override
        public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
            if (dir == Dir.opposite(kind.dir) && !canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
            if (dir == kind.dir && !B.is(neighborState, block) && !B.is(neighborState, kind.head)) {
                return headFromBody(state, headBehavior().stateForPlacement(level));
            }
            tickWater(level, pos);
            return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
        }

        @Override
        public boolean canBeReplaced(int state, PlaceContext ctx) {
            boolean r = super.canBeReplaced(state, ctx);
            return r && ctx.holding(kind.head) ? false : r;
        }

        /** BlockUtil.getTopConnectedBlock(level, pos, 몸통, 자라는 방향, 머리). */
        private Pos headPos(BlockLevel level, Pos pos) {
            Pos m = pos;
            int s;
            do {
                m = m.relative(kind.dir);
                s = level.getBlockState(m);
            } while (B.is(s, block));
            return B.is(s, kind.head) ? m : null;
        }

        @Override
        public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) {
            Pos h = headPos(level, pos);
            return h != null && headBehavior().canGrowInto(level.getBlockState(h.relative(kind.dir)));
        }

        @Override
        public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

        @Override
        public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
            Pos h = headPos(level, pos);
            if (h != null) {
                int hs = level.getBlockState(h);
                ((BonemealableBlock) B.behavior(hs)).performBonemeal(level, random, h, hs);
            }
        }
    }
}
