// 바닐라 ShulkerBoxBlock: 클릭한 면 쪽을 보게 놓인다. 열기(뚜껑이 막히지 않으면)·내용물 보존 드롭은 블록 엔티티 동작.
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

public class ShulkerBoxBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing");

    public ShulkerBoxBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[ctx.clickedFace()]); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(엔티티): 관전자 제외, 뚜껑 앞이 막혔는지(ShulkerBoxBlockEntity.canOpen — 닫힌 상태에서 열림 상자 충돌 검사), 피글린 분노.
        player.openContainer(pos);
        player.awardStat("minecraft:open_shulker_box");
        return InteractionResult.CONSUME;
    }

    @Override
    public int playerWillDestroy(BlockLevel level, Pos pos, int state, BlockActor player) {
        // TODO(블록엔티티·엔티티): 크리에이티브이고 내용물이 있으면 내용물 담은 셜커 상자 아이템을 떨군다, 아니면 전리품 풀기.
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            super.onRemove(state, level, pos, newState, moved);
            level.updateNeighbourForOutputSignal(pos, block);   // 블록 엔티티가 셜커 상자였을 때 — TODO(블록엔티티): 그 조건
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // AbstractContainerMenu.getRedstoneSignalFromBlockEntity
        return level.getBlockEntity(pos) instanceof ContainerBlockEntity c ? ChestBlock.redstoneSignalFromContainer(java.util.List.<org.domi.furnace.server.entity.ItemStack[]>of(c.items)) : 0;
    }

    @Override
    public BlockEntity newBlockEntity(Pos pos, int state) { return new org.domi.furnace.server.block.entity.ShulkerBoxBlockEntity(pos, state); }
}
