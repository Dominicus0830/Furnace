// 바닐라 VaultBlock: 금고. 바라보는 쪽을 향해 놓이고, 활성 상태에서 아이템(열쇠)을 쓰면 블록 엔티티가 보상을 내준다.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class VaultBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), STATE = B.prop("vault_state"), OMINOUS = B.prop("ominous");

    public VaultBlock(int block) { super(block); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (stack.isEmpty() || !"active".equals(STATE.valueName(state))) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // TODO(블록 엔티티): VaultBlockEntity.Server.tryInsertKey 후 SUCCESS — 블록 엔티티가 없으면 바닐라도 PASS_TO_DEFAULT.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }
}
