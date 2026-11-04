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

    /**
     * fallOn: 레벨 난수 nextFloat 가 낙하 거리 - 0.5 보다 작고 생물(플레이어거나 mobGriefing)이며 부피가 0.512 넘으면 흙. 난수는 어떤
     * 엔티티든(아이템도) 내려앉을 때마다 뽑는다.
     */
    @Override
    public void fallOn(BlockLevel level, int state, Pos pos, org.domi.furnace.server.entity.Entity entity, float fallDistance) {
        if (level.random().nextFloat() < fallDistance - 0.5F && entity.isLiving() && level.gameRule("mobGriefing")
                && entity.bbWidth() * entity.bbWidth() * entity.bbHeight() > 0.512F) {
            turnToDirt(null, state, level, pos);
        }
        super.fallOn(level, state, pos, entity, fallDistance);
    }

    /**
     * 플레이어가 내려앉았을 때의 fallOn(플레이어는 Entity 가 아니라 따로 부른다). 플레이어는 mobGriefing 과 상관없이 밟는다.
     * 낙하 피해(super.fallOn)는 PlayerVitals 가 처리한다.
     */
    public static void playerFallOn(BlockLevel level, int state, Pos pos, org.domi.furnace.server.block.BlockActor player, float fallDistance,
                                    double bbWidth, double bbHeight) {
        if (level.random().nextFloat() < fallDistance - 0.5F && (float) (bbWidth * bbWidth * bbHeight) > 0.512F) {
            turnToDirt(player, state, level, pos);
        }
    }

    public static boolean isFarmland(int state) { return B.is(state, "minecraft:farmland"); }

    /** turnToDirt: 위 엔티티를 밀어 올리고(pushEntitiesUp) 흙으로 + BLOCK_CHANGE 게임 이벤트. */
    public static void turnToDirt(BlockActor entity, int state, BlockLevel level, Pos pos) {
        int dirt = level.pushEntitiesUp(state, B.defaultState(DIRT), pos);
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
