// 바닐라 BrewingStandBlock: 양조기(블록 엔티티·화면). 비교기는 그 칸 용기 채움 정도를 읽는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class BrewingStandBlock extends BaseEntityBlock {

    public BrewingStandBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 양조기 화면 열기 + 통계 interact_with_brewingstand
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(드롭): Containers.dropContentsOnDestroy — 다른 블록이 되면 내용물을 떨군다.
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // TODO(블록엔티티): AbstractContainerMenu.getRedstoneSignalFromBlockEntity
        return 0;
    }
}
