// 바닐라 AbstractFurnaceBlock: 화로·용광로·훈연기 바탕. 바라보는 쪽을 향해 놓이고, 열면 화면, 굽기·lit 은 블록 엔티티 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;

public abstract class AbstractFurnaceBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), LIT = B.prop("lit");

    protected AbstractFurnaceBlock(int block) { super(block); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        openContainer(level, pos, player);
        return InteractionResult.CONSUME;
    }

    /** openContainer: 그 화로 화면을 열고 통계(interact_with_furnace 등). */
    protected abstract void openContainer(BlockLevel level, Pos pos, BlockActor player);

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block) && level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.FurnaceBlockEntity f) {
            level.dropContents(pos, f.items);
            // TODO(엔티티): 쌓인 경험치를 구슬로 내놓는다(getRecipesToAwardAndPopExperience) — 구슬 엔티티가 생기면.
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
}
