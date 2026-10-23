// 바닐라 BarrelBlock: 통. 바라보는 방향의 반대로 놓인다. 열림(open)·내용물은 블록 엔티티 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.block.entity.BlockEntity;
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

public class BarrelBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing");

    public BarrelBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openContainer(pos);
        player.awardStat("minecraft:open_barrel");
        // TODO(엔티티): PiglinAi.angerNearbyPiglins(player, true).
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // Containers.dropContentsOnDestroy: 다른 블록이 되면 내용물을 쏟고(열지 않은 전리품은 먼저 채운다) 비교기에 알린다
        if (!B.is(newState, block) && level.getBlockEntity(pos) instanceof ContainerBlockEntity c) {
            level.unpackContainerLoot(c);
            level.dropContents(pos, c.items);
            level.updateNeighbourForOutputSignal(pos, block);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public BlockEntity newBlockEntity(Pos pos, int state) { return new org.domi.furnace.server.block.entity.BarrelBlockEntity(pos, state); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof ContainerBlockEntity c) c.recheckOpen(level);   // BarrelBlock.tick: 여는 사람 수 다시 세기
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // AbstractContainerMenu.getRedstoneSignalFromBlockEntity
        return level.getBlockEntity(pos) instanceof ContainerBlockEntity c ? ChestBlock.redstoneSignalFromContainer(java.util.List.<org.domi.furnace.server.entity.ItemStack[]>of(c.items)) : 0;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.nearestLookingDirection())]);
    }
}
