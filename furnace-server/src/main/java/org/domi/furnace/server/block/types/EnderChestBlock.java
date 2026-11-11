// 바닐라 EnderChestBlock: 엔더 상자. 플레이어마다 따로인 엔더 인벤토리를 연다(위가 막히면 열지 않음).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.RandomSource;

public class EnderChestBlock extends AbstractChestBlock {

    static final Prop FACING = B.prop("facing");

    public EnderChestBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return WATERLOGGED.with(FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]), B.waterAt(ctx));
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // 위 칸이 레드스톤 전도체면 열리지 않는다. 열면 이 플레이어의 엔더 인벤토리(container.enderchest) + 통계 open_enderchest.
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.EnderChestBlockEntity && !ChestBlock.isBlockedChestByBlock(level, pos)) {
            player.openContainer(pos);
            player.awardStat("minecraft:open_enderchest");
            level.angerNearbyPiglins(player, true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public int updateShape(int state, int dir, int neighborState, BlockLevel level, Pos pos, Pos neighborPos) {
        B.tickWaterlogged(state, level, pos);
        return super.updateShape(state, dir, neighborState, level, pos, neighborPos);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.EnderChestBlockEntity c) c.recheckOpen(level);   // EnderChestBlock.tick
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.EnderChestBlockEntity(pos, state);
    }
}
