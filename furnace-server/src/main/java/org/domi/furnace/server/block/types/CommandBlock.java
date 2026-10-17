// 바닐라 CommandBlock: 명령 블록(일반·반복·연쇄). 바라보는 방향의 반대로 놓인다. 실행·연쇄는 블록 엔티티(CommandBlockEntity) 동작.
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

public class CommandBlock extends BaseEntityBlock {

    private static final Prop FACING = B.prop("facing");

    public CommandBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.nearestLookingDirection())]);
    }

    @Override
    public void neighborChanged(int state, BlockLevel level, Pos pos, int sourceBlock, Pos sourcePos, boolean moved) {
        // TODO(블록엔티티): 신호가 새로 들어오면(자동이 아니고 연쇄가 아니면) 1틱 뒤 실행 예약.
    }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) {
        // TODO(블록엔티티·명령): 명령 실행 → 연쇄 명령 블록 따라가기(gamerule maxCommandChainLength), 비교기 갱신.
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴): 명령 블록 편집 화면
        return player.canUseGameMasterBlocks() ? InteractionResult.CONSUME : InteractionResult.PASS;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        // TODO(블록엔티티): 마지막 실행 성공 횟수
        return 0;
    }
}
