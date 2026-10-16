// 바닐라 ChestBlock(일부): 열기. 이웃 상자와 이어 붙이기·물 잠김 등 나머지는 블록 종류 이식에서 채운다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockBehavior;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class ChestBlock extends BlockBehavior {

    public ChestBlock(int block) { super(block); }

    /** useWithoutItem: 위가 막혀 있지 않으면 화면을 연다. 결과는 언제나 CONSUME. */
    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (!isBlockedChestByBlock(level, pos)) player.openChest(pos);
        return InteractionResult.CONSUME;
    }

    /** isBlockedChestByBlock: 위 칸이 레드스톤 전도체면 막힘. */
    static boolean isBlockedChestByBlock(BlockLevel level, Pos pos) {
        Pos above = pos.above();
        int s = level.getBlockState(above);
        return org.domi.furnace.server.block.BlockBehaviors.of(s).isRedstoneConductor(s, level, above);
    }
}
