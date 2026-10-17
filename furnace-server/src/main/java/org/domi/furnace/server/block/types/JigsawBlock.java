// 바닐라 JigsawBlock: 앞(클릭 면)·위 방향 orientation. 권한 있는 크리에이티브만 화면을 연다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public class JigsawBlock extends BlockBehavior {

    private static final Prop ORIENTATION = B.prop("orientation");

    public JigsawBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        int front = ctx.clickedFace();
        int top = Dir.axis(front) == Dir.AXIS_Y ? Dir.opposite(ctx.horizontalDirection()) : Dir.UP;
        return ORIENTATION.with(defaultState(), Dir.NAME[front] + "_" + Dir.NAME[top]);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // 바닐라: 블록 엔티티가 JigsawBlockEntity 이고 권한이 있으면. TODO(블록엔티티·메뉴): 블록 엔티티 확인, 직소 블록 화면.
        return player.canUseGameMasterBlocks() ? InteractionResult.CONSUME : InteractionResult.PASS;
    }
}
