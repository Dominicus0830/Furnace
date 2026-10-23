// 바닐라 BarrelBlock: 통. 바라보는 방향의 반대로 놓인다. 열림(open)·내용물은 블록 엔티티 동작.
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

public class BarrelBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing");

    public BarrelBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴·블록엔티티): 통 화면 열기 + 통계 open_barrel + 피글린 분노
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(드롭): Containers.dropContentsOnDestroy
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        // TODO(블록엔티티): BarrelBlockEntity.recheckOpen — 여는 사람 수로 open 속성·소리
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }   // TODO(블록엔티티)

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.nearestLookingDirection())]);
    }
}
