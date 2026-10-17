// 바닐라 AbstractFurnaceBlock: 화로·용광로·훈연기 바탕. 바라보는 쪽을 향해 놓이고, 열면 화면, 굽기·lit 은 블록 엔티티 동작.
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

public abstract class AbstractFurnaceBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), LIT = B.prop("lit");

    protected AbstractFurnaceBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        openContainer(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** openContainer: 그 화로 화면을 열고 통계(interact_with_furnace 등). */
    protected abstract void openContainer(BlockLevel level, Pos pos, BlockActor player);

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(블록 엔티티): 화로 블록 엔티티면 내용물 쏟기(Containers.dropContents)·쌓인 경험치 내놓기 후 super, 그리고 updateNeighbourForOutputSignal.
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }   // TODO(블록 엔티티): AbstractContainerMenu.getRedstoneSignalFromBlockEntity
}
