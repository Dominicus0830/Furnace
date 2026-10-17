// 바닐라 CrafterBlock: 신호가 새로 들어오면 4틱 뒤 안의 재료로 제작해 앞쪽으로 내보낸다(블록 엔티티·레시피).
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
import org.domi.furnace.server.entity.ItemStack;

public class CrafterBlock extends BaseEntityBlock {

    private static final Prop CRAFTING = B.prop("crafting"), TRIGGERED = B.prop("triggered"), ORIENTATION = B.prop("orientation");

    public CrafterBlock(int block) { super(block); }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // TODO(블록엔티티): CrafterBlockEntity.getRedstoneSignal(채운 칸·잠근 칸 수)
        return 0;
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        boolean signal = level.hasNeighborSignal(pos);
        boolean triggered = TRIGGERED.is(state);
        if (signal && !triggered) {
            level.scheduleTick(pos, block, 4);
            level.setBlock(pos, TRIGGERED.with(state, true), 2);
            // TODO(블록엔티티): setTriggered(true)
        } else if (!signal && triggered) {
            level.setBlock(pos, CRAFTING.with(TRIGGERED.with(state, false), false), 2);
            // TODO(블록엔티티): setTriggered(false)
        }
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { dispenseFrom(state, level, pos); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int front = Dir.opposite(ctx.nearestLookingDirection());
        int top = switch (front) {
            case Dir.DOWN -> Dir.opposite(ctx.horizontalDirection());
            case Dir.UP -> ctx.horizontalDirection();
            default -> Dir.UP;
        };
        int s = ORIENTATION.with(defaultState(), Dir.NAME[front] + "_" + Dir.NAME[top]);
        return TRIGGERED.with(s, ctx.level().hasNeighborSignal(ctx.clickedPos()));
    }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        if (TRIGGERED.is(state)) level.scheduleTick(pos, block, 4);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(드롭): Containers.dropContentsOnDestroy
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 제작기 화면 열기
        return InteractionResult.CONSUME;
    }

    /** dispenseFrom: 레시피를 찾지 못하면 실패 소리(1050). TODO(블록엔티티·레시피): 안의 3×3 재료로 제작·내보내기·남은 재료. */
    protected void dispenseFrom(int state, BlockLevel level, Pos pos) {
        level.levelEvent(null, 1050, pos, 0);
    }
}
