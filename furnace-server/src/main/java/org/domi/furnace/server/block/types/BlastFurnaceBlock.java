// 바닐라 BlastFurnaceBlock: 용광로. 화면 열기·굽기는 블록 엔티티 동작(파티클·소리는 클라 쪽).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class BlastFurnaceBlock extends AbstractFurnaceBlock {

    public BlastFurnaceBlock(int block) { super(block); }

    @Override
    protected void openContainer(BlockLevel level, Pos pos, BlockActor player) {
        // TODO(메뉴·블록 엔티티): 블록 엔티티가 있으면 화면을 열고 통계 interact_with_blast_furnace.
    }
}
