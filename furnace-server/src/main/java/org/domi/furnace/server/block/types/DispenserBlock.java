// 바닐라 DispenserBlock: 발사기. 신호가 새로 들어오면(위 칸 신호 포함) 4틱 뒤 무작위 칸의 아이템을 내보낸다(블록 엔티티·아이템 동작).
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

public class DispenserBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), TRIGGERED = B.prop("triggered");

    public DispenserBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 발사기/공급기 화면 + 통계 inspect_dispenser / inspect_dropper
        return InteractionResult.CONSUME;
    }

    /** dispenseFrom: 빈 발사기면 실패 소리(1001) + BLOCK_ACTIVATE. TODO(블록엔티티·아이템): 무작위 칸 고르기·아이템별 발사 동작. */
    protected void dispenseFrom(BlockLevel level, int state, Pos pos) {
        level.levelEvent(null, 1001, pos, 0);
        level.gameEvent("minecraft:block_activate", pos, null, state);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos) || level.hasNeighborSignal(pos.above());
        boolean triggered = TRIGGERED.is(state);
        if (signal && !triggered) {
            level.scheduleTick(pos, block, 4);
            level.setBlock(pos, TRIGGERED.with(state, true), 2);
        } else if (!signal && triggered) {
            level.setBlock(pos, TRIGGERED.with(state, false), 2);
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { dispenseFrom(level, state, pos); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.nearestLookingDirection())]);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(드롭): Containers.dropContentsOnDestroy
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }   // TODO(블록엔티티)
}
