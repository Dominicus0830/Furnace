// 바닐라 CraftingTableBlock: 제작대. 누르면 3×3 제작 화면, 통계 interact_with_crafting_table.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class CraftingTableBlock extends BlockBehavior {

    public CraftingTableBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        openMenu(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** 화면 열기 + 통계. */
    protected void openMenu(BlockLevel level, Pos pos, BlockActor player) {
        // TODO(메뉴): 제작 화면(container.crafting).
        player.awardStat("minecraft:interact_with_crafting_table");
    }
}
