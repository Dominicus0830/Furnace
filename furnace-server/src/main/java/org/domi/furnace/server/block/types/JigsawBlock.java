// 바닐라 JigsawBlock: 앞(클릭 면)·위 방향 orientation. 권한 있는 크리에이티브만 화면을 연다(화면은 클라이언트가 블록 엔티티 데이터로 직접 연다).
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
import org.domi.furnace.server.block.entity.BlockEntity;
import org.domi.furnace.server.block.entity.JigsawBlockEntity;

public class JigsawBlock extends BaseEntityBlock {

    private static final Prop ORIENTATION = B.prop("orientation");

    public JigsawBlock(int block) { super(block); }

    @Override
    public BlockEntity newBlockEntity(Pos pos, int state) { return new JigsawBlockEntity(pos, state); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int front = ctx.clickedFace();
        int top = Dir.axis(front) == Dir.AXIS_Y ? Dir.opposite(ctx.horizontalDirection()) : Dir.UP;
        return ORIENTATION.with(defaultState(), Dir.NAME[front] + "_" + Dir.NAME[top]);
    }

    /** 블록 엔티티가 JigsawBlockEntity 이고 권한 있는 크리에이티브면 소비(화면은 클라이언트가 연다), 아니면 넘긴다. */
    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        return level.getBlockEntity(pos) instanceof JigsawBlockEntity && player.canUseGameMasterBlocks() ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
