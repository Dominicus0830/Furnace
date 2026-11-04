// 바닐라 BigDripleafBlock: 큰 흘림잎 머리. 위에 올라서면 기울어(불안정→일부→완전) 떨어뜨리고, 신호를 받으면 바로 선다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class BigDripleafBlock extends HorizontalDirectionalBlock implements BonemealableBlock {

    static final Prop TILT = B.prop("tilt");
    static final int BIG_DRIPLEAF = B.id("big_dripleaf"), BIG_DRIPLEAF_STEM = B.id("big_dripleaf_stem");
    private static final int WATER = B.id("water"), SMALL_DRIPLEAF = B.id("small_dripleaf");

    public BigDripleafBlock(int block) { super(block); }

    /** 기울기별 다음 틱까지(불안정·일부 10, 완전 100, 없음 -1). */
    private static int delayAfter(String tilt) {
        return switch (tilt) {
            case "unstable", "partial" -> 10;
            case "full" -> 100;
            default -> -1;
        };
    }

    /** Tilt.causesVibration: 불안정만 거짓. */
    private static boolean causesVibration(String tilt) { return !"unstable".equals(tilt); }

    /** placeWithRandomHeight: 2~5 칸(놓을 수 있는 데까지) 줄기를 세우고 맨 위에 잎. */
    public static void placeWithRandomHeight(BlockLevel level, RandomSource random, Pos pos, int facing) {
        int height = FrostedIceBlock.nextInt(random, 2, 5);
        Pos m = pos;
        int i = 0;
        while (i < height && canPlaceAt(level, m, level.getBlockState(m))) {
            i++;
            m = m.above();
        }
        int top = pos.y() + i - 1;
        m = new Pos(m.x(), pos.y(), m.z());
        while (m.y() < top) {
            BigDripleafStemBlock.place(level, m, level.getFluidState(m), facing);
            m = m.above();
        }
        place(level, m, level.getFluidState(m), facing);
    }

    private static boolean canReplace(int state) { return B.isAir(state) || B.is(state, WATER) || B.is(state, SMALL_DRIPLEAF); }

    static boolean canPlaceAt(BlockLevel level, Pos pos, int state) { return !level.isOutsideBuildHeight(pos.y()) && canReplace(state); }

    static boolean place(BlockLevel level, Pos pos, int fluid, int facing) {
        int s = WATERLOGGED.with(B.defaultState(BIG_DRIPLEAF), Fluids.isWaterSource(fluid));
        return level.setBlock(pos, FACING.with(s, Dir.NAME[facing]), 3);
    }

    /** onProjectileHit: 맞으면 바로 완전히 기울어진다(소리 tilt_down, 100틱 뒤 되돌림). */
    @Override
    public void onProjectileHit(BlockLevel level, int state, org.domi.furnace.server.entity.Projectile.Hit hit,
                                org.domi.furnace.server.entity.Projectile projectile) {
        setTiltAndScheduleTick(state, level, hit.blockPos(), "full", true);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int below = level.getBlockState(pos.below());
        return B.is(below, block) || B.is(below, BIG_DRIPLEAF_STEM) || B.isTag(below, "minecraft:big_dripleaf_placeable");
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.DOWN && !canSurvive(state, level, pos)) return B.defaultState("air");
        B.tickWaterlogged(state, level, pos);
        return dir == Dir.UP && B.is(neighborState, block)
                ? B.withPropertiesOf(BIG_DRIPLEAF_STEM, state) : super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean isValidBonemealTarget(BlockLevel level, Pos pos, int state) { return canReplace(level.getBlockState(pos.above())); }

    @Override
    public boolean isBonemealSuccess(BlockLevel level, RandomSource random, Pos pos, int state) { return true; }

    @Override
    public void performBonemeal(BlockLevel level, RandomSource random, Pos pos, int state) {
        Pos above = pos.above();
        int as = level.getBlockState(above);
        if (canPlaceAt(level, above, as)) {
            int facing = Dir.byName(FACING.valueName(state));
            BigDripleafStemBlock.place(level, pos, B.fluid(state), facing);
            place(level, above, B.fluid(as), facing);
        }
    }

    // TODO(엔티티): entityInside — 기울지 않은 잎 위에 선 엔티티가 있고 신호가 없으면 불안정으로.

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.hasNeighborSignal(pos)) {
            resetTilt(state, level, pos);
            return;
        }
        switch (TILT.valueName(state)) {
            case "unstable" -> setTiltAndScheduleTick(state, level, pos, "partial", true);
            case "partial" -> setTiltAndScheduleTick(state, level, pos, "full", true);
            case "full" -> resetTilt(state, level, pos);
            default -> {}
        }
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (level.hasNeighborSignal(pos)) resetTilt(state, level, pos);
    }

    private static void playTiltSound(BlockLevel level, Pos pos, String sound) {
        float pitch = level.random().nextFloat() * (1.2F - 0.8F) + 0.8F;
        level.playSound(null, pos, sound, "blocks", 1.0F, pitch);
    }

    private void setTiltAndScheduleTick(int state, BlockLevel level, Pos pos, String tilt, boolean sound) {
        setTilt(state, level, pos, tilt);
        if (sound) playTiltSound(level, pos, "minecraft:block.big_dripleaf.tilt_down");
        int delay = delayAfter(tilt);
        if (delay != -1) level.scheduleTick(pos, block, delay);
    }

    private static void resetTilt(int state, BlockLevel level, Pos pos) {
        setTilt(state, level, pos, "none");
        if (!"none".equals(TILT.valueName(state))) playTiltSound(level, pos, "minecraft:block.big_dripleaf.tilt_up");
    }

    private static void setTilt(int state, BlockLevel level, Pos pos, String tilt) {
        String old = TILT.valueName(state);
        level.setBlock(pos, TILT.with(state, tilt), 2);
        if (causesVibration(tilt) && !tilt.equals(old)) level.gameEvent("minecraft:block_change", pos, null, -1);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int below = ctx.level().getBlockState(ctx.clickedPos().below());
        int fluid = ctx.level().getFluidState(ctx.clickedPos());
        boolean connected = B.is(below, BIG_DRIPLEAF) || B.is(below, BIG_DRIPLEAF_STEM);
        int s = WATERLOGGED.with(defaultState(), Fluids.isWaterSource(fluid));
        return FACING.with(s, connected ? FACING.valueName(below) : Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }
}
