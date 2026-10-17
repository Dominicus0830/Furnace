// 바닐라 CartographyTableBlock: 쓰면 지도 제작대 화면.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class CartographyTableBlock extends BlockBehavior {

    public CartographyTableBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(메뉴): 지도 제작대 화면(container.cartography_table) + 통계 interact_with_cartography_table
        return InteractionResult.CONSUME;
    }
}
