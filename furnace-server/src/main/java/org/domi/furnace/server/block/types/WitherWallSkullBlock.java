// 바닐라 WitherWallSkullBlock: 벽 위더 해골 — 놓이면 위더 소환 검사.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.ItemStack;

public class WitherWallSkullBlock extends WallSkullBlock {

    public WitherWallSkullBlock(int block) { super(block); }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) { WitherSkullBlock.checkSpawn(level, pos); }
}
