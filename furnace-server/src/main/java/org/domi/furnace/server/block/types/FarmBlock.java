// 바닐라 FarmBlock: 경작지. 위가 단단하면 흙이 된다. 무작위 틱으로 물기(moisture)를 얻고 잃는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class FarmBlock extends BlockBehavior {

    private static final Prop MOISTURE = B.prop("moisture");
    private static final int DIRT = B.id("dirt"), MOVING_PISTON = B.id("moving_piston");

    public FarmBlock(int block) { super(block); }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        if (dir == Dir.UP && !canSurvive(state, level, pos)) level.scheduleTick(pos, block, 1);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public boolean canSurvive(int state, BlockLevel level, Pos pos) {
        int above = level.getBlockState(pos.above());
        return !B.isSolid(above) || B.behavior(above) instanceof FenceGateBlock || B.is(above, MOVING_PISTON);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return !canSurvive(defaultState(), ctx.level(), ctx.clickedPos()) ? B.defaultState(DIRT) : super.getStateForPlacement(ctx);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (!canSurvive(state, level, pos)) turnToDirt(null, state, level, pos);
    }

    @Override
    public void randomTick(int state, BlockLevel level, Pos pos, RandomSource random) {
        int m = MOISTURE.intValue(state);
        if (!isNearWater(level, pos) && !level.isRainingAt(pos.above())) {
            if (m > 0) level.setBlock(pos, MOISTURE.withInt(state, m - 1), 2);
            else if (!shouldMaintainFarmland(level, pos)) turnToDirt(null, state, level, pos);
        } else if (m < 7) {
            level.setBlock(pos, MOISTURE.withInt(state, 7), 2);
        }
    }

    // TODO(엔티티): fallOn — 높이 떨어진 엔티티가 밟으면 흙이 된다.

    /** turnToDirt: 흙으로(위 엔티티 밀어 올리기는 엔티티 체계 뒤) + BLOCK_CHANGE 게임 이벤트. */
    public static void turnToDirt(BlockActor entity, int state, BlockLevel level, Pos pos) {
        int dirt = B.defaultState(DIRT);   // TODO(엔티티): Block.pushEntitiesUp
        level.setBlockAndUpdate(pos, dirt);
        level.gameEvent("minecraft:block_change", pos, entity, dirt);
    }

    private static boolean shouldMaintainFarmland(BlockLevel level, Pos pos) {
        return B.isTag(level.getBlockState(pos.above()), "minecraft:maintains_farmland");
    }

    private static boolean isNearWater(BlockLevel level, Pos pos) {
        for (int y = 0; y <= 1; y++) {
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (Fluids.isWater(level.getFluidState(pos.offset(x, y, z)))) return true;
                }
            }
        }
        return false;
    }
}
