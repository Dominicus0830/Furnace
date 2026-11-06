// 바닐라 StonecutterBlock: 석재 절단기 — 바라보는 방향의 반대로 놓이고, 쓰면 절단기 화면.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;

public class StonecutterBlock extends HorizontalDirectionalBlock {

    public StonecutterBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) { return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openStonecutter(pos);
        player.awardStat("minecraft:interact_with_stonecutter");
        return InteractionResult.CONSUME;
    }
}
