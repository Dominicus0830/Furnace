// 바닐라 BrewingStandBlock: 양조기(블록 엔티티·화면). 비교기는 그 칸 용기 채움 정도를 읽는다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.Pos;

public class BrewingStandBlock extends BaseEntityBlock {

    public BrewingStandBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        player.openContainer(pos);
        player.awardStat("minecraft:interact_with_brewingstand");
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!org.domi.furnace.server.block.B.is(newState, block) && level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c) {
            level.dropContents(pos, c.items);
            level.updateNeighbourForOutputSignal(pos, block);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.ContainerBlockEntity c
                ? ChestBlock.redstoneSignalFromContainer(java.util.List.<org.domi.furnace.server.entity.ItemStack[]>of(c.items)) : 0;
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.BrewingStandBlockEntity(pos, state);
    }
}
