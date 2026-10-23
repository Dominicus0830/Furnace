// 바닐라 SmithingTableBlock: 대장장이 작업대. 누르면 강화 화면(container.upgrade), 통계 interact_with_smithing_table.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class SmithingTableBlock extends CraftingTableBlock {

    public SmithingTableBlock(int block) { super(block); }

    @Override
    protected void openMenu(BlockLevel level, Pos pos, BlockActor player) {
        player.openSmithing(pos);
        player.awardStat("minecraft:interact_with_smithing_table");
    }
}
