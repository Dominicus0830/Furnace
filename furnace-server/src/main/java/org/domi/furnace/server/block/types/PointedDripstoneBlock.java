// 바닐라 PointedDripstoneBlock: 뾰족한 점적석. 종유석(아래 끝)·석순(위 끝)과 굵기(tip_merge/tip/frustum/middle/base),
// 무작위 틱의 물·용암 떨어뜨리기(가마솥 채우기·진흙→점토)와 자라기, 받침이 없어지면 떨어지거나 부서진다.
package org.domi.furnace.server.block.types;

import java.util.function.BiPredicate;
import java.util.function.IntPredicate;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class PointedDripstoneBlock extends BlockBehavior {

    static final Prop TIP_DIRECTION = B.prop("vertical_direction"), THICKNESS = B.prop("thickness");
    private static final String TIP_MERGE = "tip_merge", TIP = "tip", FRUSTUM = "frustum", MIDDLE = "middle", BASE = "base";
    private static final int POINTED_DRIPSTONE = B.id("pointed_dripstone"), DRIPSTONE_BLOCK = B.id("dripstone_block"), MUD = B.id("mud"),
            CLAY = B.id("clay"), WATER = B.id("water");
    private static final float WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.17578125F, LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK = 0.05859375F,
            GROWTH_PROBABILITY_PER_RANDOM_TICK = 0.011377778F;

    public PointedDripstoneBlock(int block) { super(block); }

    private static int tipDir(int state) { return Dir.byName(TIP_DIRECTION.valueName(state)); }

    private static String thickness(int state) { return THICKNESS.valueName(state); }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) { return isValidPointedDripstonePlacement(level, pos, tipDir(state)); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        if (dir != Dir.UP && dir != Dir.DOWN) return state;
        int tip = tipDir(state);
        if (tip == Dir.DOWN && level.hasScheduledTick(pos, block)) return state;
        if (dir == Dir.opposite(tip) && !canSurvive(state, level, pos)) {
            level.scheduleTick(pos, block, tip == Dir.DOWN ? 2 : 1);
            return state;
        }
        boolean mergeOpposite = TIP_MERGE.equals(thickness(state));
        return THICKNESS.with(state, calculateDripstoneThickness(level, pos, tip, mergeOpposite));
    }

    // TODO(엔티티): onProjectileHit(속도 0.6 넘는 삼지창이면 부순다)·fallOn(석순 끝이면 피해 2배)·onBrokenAfterFall(1045).

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (isStalagmite(state) && !canSurvive(state, level, pos)) {
            level.destroyBlock(pos, true);
        } else {
            spawnFallingStalactite(state, level, pos);
        }
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        maybeTransferFluid(state, level, pos, random.nextFloat());
        if (random.nextFloat() < GROWTH_PROBABILITY_PER_RANDOM_TICK && isStalactiteStartPos(state, level, pos)) {
            growStalactiteOrStalagmiteIfPossible(state, level, pos, random);
        }
    }

    /** maybeTransferFluid: 뿌리 위 유체(진흙이면 물)를 끝으로 떨어뜨려 진흙을 점토로 바꾸거나 아래 가마솥에 채울 틱을 건다. */
    public static void maybeTransferFluid(int state, BlockLevel level, Pos pos, float chance) {
        if (chance > WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK && chance > LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK) return;
        if (!isStalactiteStartPos(state, level, pos)) return;
        FluidInfo info = getFluidAboveStalactite(level, pos, state);
        if (info == null) return;
        int fluid = info.fluid;
        float p;
        if (fluid == Fluids.T_WATER) p = WATER_TRANSFER_PROBABILITY_PER_RANDOM_TICK;
        else if (fluid == Fluids.T_LAVA) p = LAVA_TRANSFER_PROBABILITY_PER_RANDOM_TICK;
        else return;
        if (chance >= p) return;
        Pos tip = findTip(state, level, pos, 11, false);
        if (tip == null) return;
        if (B.is(info.sourceState, MUD) && fluid == Fluids.T_WATER) {
            int clay = B.defaultState(CLAY);
            level.setBlockAndUpdate(info.pos, clay);
            // TODO(엔티티): Block.pushEntitiesUp(진흙, 점토, level, pos).
            level.gameEvent("minecraft:block_change", info.pos, null, clay);
            level.levelEvent(null, 1504, tip, 0);
        } else {
            Pos cauldron = findFillableCauldronBelowStalactiteTip(level, tip, fluid);
            if (cauldron == null) return;
            level.levelEvent(null, 1504, tip, 0);
            int delay = 50 + tip.y() - cauldron.y();
            level.scheduleTick(cauldron, B.block(level.getBlockState(cauldron)), delay);
        }
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        BlockLevel level = ctx.level();
        Pos pos = ctx.clickedPos();
        int dir = Dir.opposite(ctx.nearestLookingVerticalDirection());
        int tip = calculateTipDirection(level, pos, dir);
        if (tip < 0) return -1;
        String t = calculateDripstoneThickness(level, pos, tip, !ctx.isSecondaryUseActive());
        int s = THICKNESS.with(TIP_DIRECTION.with(defaultState(), Dir.NAME[tip]), t);
        return WATERLOGGED.with(s, B.waterAt(ctx));
    }

    /** spawnFallingStalactite: 종유석 줄기를 끝까지 떨어뜨린다. */
    private static void spawnFallingStalactite(int state, BlockLevel level, Pos pos) {
        // TODO(엔티티): FallingBlockEntity.fall — 위에서부터 끝(tip)까지 칸마다 떨어지는 블록을 만들고, 끝 블록에 피해(높이 기반, 최대 40)를 준다.
    }

    /** growStalactiteOrStalagmiteIfPossible: 위가 점적석 블록+물 원천이면 끝을 아래로 늘리거나 바닥 석순을 키운다. */
    public static void growStalactiteOrStalagmiteIfPossible(int state, BlockLevel level, Pos pos, RandomSource random) {
        int s1 = level.getBlockState(pos.above(1)), s2 = level.getBlockState(pos.above(2));
        if (!canGrow(s1, s2)) return;
        Pos tip = findTip(state, level, pos, 7, false);
        if (tip == null) return;
        int tipState = level.getBlockState(tip);
        if (!canDrip(tipState) || !canTipGrow(tipState, level, tip)) return;
        if (random.nextBoolean()) grow(level, tip, Dir.DOWN);
        else growStalagmiteBelow(level, tip);
    }

    private static void growStalagmiteBelow(BlockLevel level, Pos pos) {
        Pos p = pos;
        for (int i = 0; i < 10; i++) {
            p = p.below();
            int s = level.getBlockState(p);
            if (!Fluids.isEmpty(B.fluid(s))) return;
            if (isUnmergedTipWithDirection(s, Dir.UP) && canTipGrow(s, level, p)) {
                grow(level, p, Dir.UP);
                return;
            }
            if (isValidPointedDripstonePlacement(level, p, Dir.UP) && !level.isWaterAt(p.below())) {
                grow(level, p.below(), Dir.UP);
                return;
            }
            if (!canDripThrough(level, p, s)) return;
        }
    }

    private static void grow(BlockLevel level, Pos pos, int dir) {
        Pos target = pos.relative(dir);
        int s = level.getBlockState(target);
        if (isUnmergedTipWithDirection(s, Dir.opposite(dir))) {
            createMergedTips(s, level, target);
        } else if (B.isAir(s) || B.is(s, WATER)) {
            createDripstone(level, target, dir, TIP);
        }
    }

    private static void createDripstone(BlockLevel level, Pos pos, int dir, String thickness) {
        int s = THICKNESS.with(TIP_DIRECTION.with(B.defaultState(POINTED_DRIPSTONE), Dir.NAME[dir]), thickness);
        level.setBlock(pos, WATERLOGGED.with(s, Fluids.type(level.getFluidState(pos)) == Fluids.T_WATER), 3);
    }

    private static void createMergedTips(int state, BlockLevel level, Pos pos) {
        Pos stalagmite, stalactite;
        if (tipDir(state) == Dir.UP) {
            stalagmite = pos;
            stalactite = pos.above();
        } else {
            stalactite = pos;
            stalagmite = pos.below();
        }
        createDripstone(level, stalactite, Dir.DOWN, TIP_MERGE);
        createDripstone(level, stalagmite, Dir.UP, TIP_MERGE);
    }

    private static Pos findTip(int state, BlockLevel level, Pos pos, int maxIter, boolean includeMerge) {
        if (isTip(state, includeMerge)) return pos;
        int tip = tipDir(state);
        BiPredicate<Pos, Integer> along = (p, s) -> B.is(s, POINTED_DRIPSTONE) && tipDir(s) == tip;
        return findBlockVertical(level, pos, Dir.positive(tip), along, s -> isTip(s, includeMerge), maxIter);
    }

    /** calculateTipDirection: 원하는 쪽에 붙을 수 있으면 그쪽, 아니면 반대쪽, 둘 다 안 되면 -1. */
    private static int calculateTipDirection(BlockLevel level, Pos pos, int dir) {
        if (isValidPointedDripstonePlacement(level, pos, dir)) return dir;
        if (!isValidPointedDripstonePlacement(level, pos, Dir.opposite(dir))) return -1;
        return Dir.opposite(dir);
    }

    private static String calculateDripstoneThickness(BlockLevel level, Pos pos, int dir, boolean mergeOpposite) {
        int opp = Dir.opposite(dir);
        int s = level.getBlockState(pos.relative(dir));
        if (isPointedDripstoneWithDirection(s, opp)) {
            return !mergeOpposite && !TIP_MERGE.equals(thickness(s)) ? TIP : TIP_MERGE;
        }
        if (!isPointedDripstoneWithDirection(s, dir)) return TIP;
        String t = thickness(s);
        if (!TIP.equals(t) && !TIP_MERGE.equals(t)) {
            int s2 = level.getBlockState(pos.relative(opp));
            return !isPointedDripstoneWithDirection(s2, dir) ? BASE : MIDDLE;
        }
        return FRUSTUM;
    }

    /** canDrip: 물에 잠기지 않은 종유석 끝. */
    public static boolean canDrip(int state) { return isStalactite(state) && TIP.equals(thickness(state)) && !WATERLOGGED.is(state); }

    private static boolean canTipGrow(int state, BlockLevel level, Pos pos) {
        int dir = tipDir(state);
        int s = level.getBlockState(pos.relative(dir));
        if (!Fluids.isEmpty(B.fluid(s))) return false;
        return B.isAir(s) || isUnmergedTipWithDirection(s, Dir.opposite(dir));
    }

    private static Pos findRootBlock(BlockLevel level, Pos pos, int state, int maxIter) {
        int tip = tipDir(state);
        BiPredicate<Pos, Integer> along = (p, s) -> B.is(s, POINTED_DRIPSTONE) && tipDir(s) == tip;
        return findBlockVertical(level, pos, Dir.positive(Dir.opposite(tip)), along, s -> !B.is(s, POINTED_DRIPSTONE), maxIter);
    }

    private static boolean isValidPointedDripstonePlacement(BlockLevel level, Pos pos, int dir) {
        Pos p = pos.relative(Dir.opposite(dir));
        int s = level.getBlockState(p);
        return B.isFaceSturdy(s, level, p, dir) || isPointedDripstoneWithDirection(s, dir);
    }

    private static boolean isTip(int state, boolean includeMerge) {
        if (!B.is(state, POINTED_DRIPSTONE)) return false;
        String t = thickness(state);
        return TIP.equals(t) || includeMerge && TIP_MERGE.equals(t);
    }

    private static boolean isUnmergedTipWithDirection(int state, int dir) { return isTip(state, false) && tipDir(state) == dir; }

    private static boolean isStalactite(int state) { return isPointedDripstoneWithDirection(state, Dir.DOWN); }

    private static boolean isStalagmite(int state) { return isPointedDripstoneWithDirection(state, Dir.UP); }

    private static boolean isStalactiteStartPos(int state, BlockLevel level, Pos pos) {
        return isStalactite(state) && !B.is(level.getBlockState(pos.above()), POINTED_DRIPSTONE);
    }

    private static boolean isPointedDripstoneWithDirection(int state, int dir) { return B.is(state, POINTED_DRIPSTONE) && tipDir(state) == dir; }

    private static Pos findFillableCauldronBelowStalactiteTip(BlockLevel level, Pos pos, int fluid) {
        IntPredicate cauldron = s -> B.behavior(s) instanceof AbstractCauldronBlock c && c.canReceiveStalactiteDrip(fluid);
        return findBlockVertical(level, pos, false, (p, s) -> canDripThrough(level, p, s), cauldron, 11);
    }

    /** findStalactiteTipAboveCauldron: 가마솥 위로 물이 떨어질 종유석 끝. */
    public static Pos findStalactiteTipAboveCauldron(BlockLevel level, Pos pos) {
        return findBlockVertical(level, pos, true, (p, s) -> canDripThrough(level, p, s), PointedDripstoneBlock::canDrip, 11);
    }

    /** getCauldronFillFluidType: 그 종유석 뿌리 위의 물·용암(없으면 EMPTY). */
    public static int getCauldronFillFluidType(BlockLevel level, Pos pos) {
        FluidInfo info = getFluidAboveStalactite(level, pos, level.getBlockState(pos));
        return info != null && canFillCauldron(info.fluid) ? info.fluid : Fluids.EMPTY;
    }

    private record FluidInfo(Pos pos, int fluid, int sourceState) {}

    private static FluidInfo getFluidAboveStalactite(BlockLevel level, Pos pos, int state) {
        if (!isStalactite(state)) return null;
        Pos root = findRootBlock(level, pos, state, 11);
        if (root == null) return null;
        Pos above = root.above();
        int s = level.getBlockState(above);
        int fluid = B.is(s, MUD) && !level.ultraWarm() ? Fluids.T_WATER : Fluids.type(level.getFluidState(above));
        return new FluidInfo(above, fluid, s);
    }

    private static boolean canFillCauldron(int fluid) { return fluid == Fluids.T_LAVA || fluid == Fluids.T_WATER; }

    private static boolean canGrow(int s1, int s2) {
        return B.is(s1, DRIPSTONE_BLOCK) && B.is(s2, WATER) && Fluids.isSource(B.fluid(s2));
    }

    /** findBlockVertical: 위(up=true)나 아래로 한 칸씩, matcher 가 맞으면 그 칸, 높이 밖이거나 along 이 거짓이면 멈춘다. */
    private static Pos findBlockVertical(BlockLevel level, Pos pos, boolean up, BiPredicate<Pos, Integer> along, IntPredicate matcher, int maxIter) {
        int dir = up ? Dir.UP : Dir.DOWN;
        Pos p = pos;
        for (int i = 1; i < maxIter; i++) {
            p = p.relative(dir);
            int s = level.getBlockState(p);
            if (matcher.test(s)) return p;
            if (level.isOutsideBuildHeight(p.y()) || !along.test(p, s)) return null;
        }
        return null;
    }

    /** canDripThrough: 공기이거나, 꽉 찬 블록이 아니고 유체가 없으며 가운데 4×4 기둥을 막지 않는 칸. */
    private static boolean canDripThrough(BlockLevel level, Pos pos, int state) {
        if (B.isAir(state)) return true;
        if (B.isFullOcclusion(state)) return false;
        if (!Fluids.isEmpty(B.fluid(state))) return false;
        return !B.behavior(state).getCollisionShape(state, level, pos, null).intersects(6 / 16.0, 0, 6 / 16.0, 10 / 16.0, 1, 10 / 16.0);
    }
}
