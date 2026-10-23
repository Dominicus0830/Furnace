// 바닐라 ShulkerBoxBlock: 클릭한 면 쪽을 보게 놓인다. 열기(뚜껑이 막히지 않으면)·내용물 보존 드롭은 블록 엔티티 동작.
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

public class ShulkerBoxBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing");

    public ShulkerBoxBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[ctx.clickedFace()]); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 관전자가 아니고 뚜껑 앞이 막히지 않았으면(닫힌 상태에서 열림 상자와 충돌 검사) 화면 열기
        //  + 통계 open_shulker_box + 피글린 분노.
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
        return 0;   // TODO(블록엔티티): AbstractContainerMenu.getRedstoneSignalFromBlockEntity
    }
}
