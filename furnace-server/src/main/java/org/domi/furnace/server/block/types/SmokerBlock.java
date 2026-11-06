// 바닐라 SmokerBlock: 훈연기. 화면 열기·굽기는 블록 엔티티 동작(파티클·소리는 클라 쪽).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;

public class SmokerBlock extends AbstractFurnaceBlock {

    public SmokerBlock(int block) { super(block); }

    @Override
    protected void openContainer(BlockLevel level, Pos pos, BlockActor player) {
        player.openContainer(pos);
        player.awardStat("minecraft:interact_with_smoker");
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return org.domi.furnace.server.block.entity.FurnaceBlockEntity.smoker(pos, state);
    }
}
