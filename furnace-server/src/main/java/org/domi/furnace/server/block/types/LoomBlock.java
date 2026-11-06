// 바닐라 LoomBlock: 베틀. 바라보는 쪽을 향해 놓이고, 누르면 베틀 화면, 통계 interact_with_loom.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class LoomBlock extends HorizontalDirectionalBlock {

    public LoomBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openLoom(pos);
        player.awardStat("minecraft:interact_with_loom");
        return InteractionResult.CONSUME;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }
}
