// 바닐라 FletchingTableBlock: 화살대. 화면이 없어서 누르면 PASS.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class FletchingTableBlock extends CraftingTableBlock {

    public FletchingTableBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) { return InteractionResult.PASS; }
}
