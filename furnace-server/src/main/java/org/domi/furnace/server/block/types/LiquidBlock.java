// 바닐라 LiquidBlock: 물·용암 블록. level 0 = 원천, 1~7 = 흐름(양 8-level), 8 이상 = 떨어지는 흐름.
// 놓이거나 이웃이 바뀌면 용암·물이 만나 흑요석·조약돌·현무암이 되는지 보고 유체 틱을 건다. 흐름 계산은 FluidTicker(3-4d).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class LiquidBlock extends BlockBehavior {

    private static final Prop LEVEL = B.prop("level");
    /** POSSIBLE_FLOW_DIRECTIONS. */
    private static final int[] FLOW_DIRS = {Dir.DOWN, Dir.SOUTH, Dir.NORTH, Dir.EAST, Dir.WEST};
    private static final int SOUL_SOIL = B.id("soul_soil"), BLUE_ICE = B.id("blue_ice"), OBSIDIAN = B.id("obsidian"),
            COBBLESTONE = B.id("cobblestone"), BASALT = B.id("basalt"), WATER = B.id("water");
    private final boolean water;

    public LiquidBlock(int block) {
        super(block);
        this.water = block == WATER;
    }

    @Override
    public int getFluidState(int state) {
        int level = LEVEL.intValue(state);
        if (level == 0) return water ? Fluids.WATER_SOURCE : Fluids.LAVA_SOURCE;
        if (level < 8) return Fluids.flowing(water, 8 - level, false);
        return Fluids.flowing(water, 8, true);
    }

    @Override
    public boolean isRandomlyTicking(int state) { return !water; }   // 용암만(LavaFluid.isRandomlyTicking)

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        // TODO(유체): FluidState.randomTick — 용암 불 번지기(LavaFluid.randomTick)
    }

    private int tickDelay(BlockLevel level) { return Fluids.tickDelay(water ? Fluids.WATER_SOURCE : Fluids.LAVA_SOURCE, level.ultraWarm()); }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (shouldSpreadLiquid(level, pos, state)) level.scheduleFluidTick(pos, Fluids.type(getFluidState(state)), tickDelay(level));
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (Fluids.isSource(getFluidState(state)) || Fluids.isSource(B.fluid(neighborState))) {
            level.scheduleFluidTick(pos, Fluids.type(getFluidState(state)), tickDelay(level));
        }
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        if (shouldSpreadLiquid(level, pos, state)) level.scheduleFluidTick(pos, Fluids.type(getFluidState(state)), tickDelay(level));
    }

    /** shouldSpreadLiquid: 용암이 물을 만나면 흑요석(원천)·조약돌, 영혼 흙 위에서 푸른 얼음을 만나면 현무암. */
    private boolean shouldSpreadLiquid(BlockLevel level, Pos pos, int state) {
        if (!water) {
            boolean soulSoil = B.is(level.getBlockState(pos.below()), SOUL_SOIL);
            for (int d : FLOW_DIRS) {
                Pos n = pos.relative(Dir.opposite(d));
                if (Fluids.isWater(level.getFluidState(n))) {
                    int b = Fluids.isSource(level.getFluidState(pos)) ? OBSIDIAN : COBBLESTONE;
                    level.setBlockAndUpdate(pos, B.defaultState(b));
                    fizz(level, pos);
                    return false;
                }
                if (soulSoil && B.is(level.getBlockState(n), BLUE_ICE)) {
                    level.setBlockAndUpdate(pos, B.defaultState(BASALT));
                    fizz(level, pos);
                    return false;
                }
            }
        }
        return true;
    }

    private static void fizz(BlockLevel level, Pos pos) { level.levelEvent(null, 1501, pos, 0); }
}
