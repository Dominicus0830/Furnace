// 바닐라 HopperBlock: 깔때기. 클릭한 면 쪽으로 내보낸다(위면을 눌렀으면 아래로). 신호를 받으면 꺼진다(enabled=false).
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

public class HopperBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing"), ENABLED = B.prop("enabled");

    public HopperBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int d = Dir.opposite(ctx.clickedFace());
        return ENABLED.with(FACING.with(defaultState(), Dir.NAME[Dir.axis(d) == Dir.AXIS_Y ? Dir.DOWN : d]), true);
    }

    @Override
    public void onPlace(int state, BlockLevel level, Pos pos, int oldState, boolean moved) {
        if (!B.is(oldState, B.block(state))) checkPoweredState(level, pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 깔때기 화면 + 통계 inspect_hopper
        return InteractionResult.CONSUME;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        checkPoweredState(level, pos, state);
    }

    private static void checkPoweredState(BlockLevel level, Pos pos, int state) {
        boolean enabled = !level.hasNeighborSignal(pos);
        if (enabled != ENABLED.is(state)) level.setBlock(pos, ENABLED.with(state, enabled), 2);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(드롭): Containers.dropContentsOnDestroy
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // TODO(블록엔티티): AbstractContainerMenu.getRedstoneSignalFromBlockEntity
        return 0;
    }

    // TODO(엔티티·블록엔티티): entityInside — 위에 떨어진 아이템 엔티티 빨아들이기, 블록 엔티티 틱(옮기기·쿨다운).
}
