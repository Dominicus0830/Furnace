// 바닐라 ChiseledBookShelfBlock: 조각된 책장. 앞면 6칸(위 3·아래 3) 중 맞은 칸에 책을 꽂거나 뺀다(칸 내용은 블록 엔티티).
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.PlaceContext;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;

public class ChiseledBookShelfBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing");
    static final Prop[] SLOT_OCCUPIED = {B.prop("slot_0_occupied"), B.prop("slot_1_occupied"), B.prop("slot_2_occupied"),
            B.prop("slot_3_occupied"), B.prop("slot_4_occupied"), B.prop("slot_5_occupied")};

    public ChiseledBookShelfBlock(int block) { super(block); }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        // TODO(블록 엔티티): ChiseledBookShelfBlockEntity 가 있으면 #bookshelf_books 아이템을 맞은 빈 칸에 꽂는다
        //   (getHitSlot 이 없으면 SKIP_DEFAULT, 찬 칸이면 PASS_TO_DEFAULT, 꽂으면 CONSUME·소리 insert(.enchanted)). 블록 엔티티가 없으면 바닐라도 SKIP_DEFAULT.
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        // TODO(블록 엔티티): 맞은 칸의 책을 빼서 인벤토리(안 되면 바닥)로, 소리 pickup(.enchanted)·block_change. 블록 엔티티가 없으면 바닐라도 PASS.
        return InteractionResult.PASS;
    }

    /** getHitSlot: 앞면을 맞혔으면 그 칸 번호(0..5), 아니면 -1. */
    static int getHitSlot(Hit hit, int state) {
        int facing = Dir.byName(FACING.valueName(state));
        if (hit.face() != facing) return -1;
        Pos rel = hit.pos().relative(facing);
        double x = hit.x() - rel.x(), y = hit.y() - rel.y(), z = hit.z() - rel.z();
        float u, v = (float) y;
        switch (facing) {
            case Dir.NORTH -> u = (float) (1.0 - x);
            case Dir.SOUTH -> u = (float) x;
            case Dir.WEST -> u = (float) z;
            case Dir.EAST -> u = (float) (1.0 - z);
            default -> { return -1; }
        }
        int row = v >= 0.5F ? 0 : 1;
        int col = u < 0.375F ? 0 : u < 0.6875F ? 1 : 2;
        return col + row * 3;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        // TODO(블록 엔티티): 다른 블록이 되면 6칸 책을 쏟고 비운 뒤 updateNeighbourForOutputSignal.
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        return FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
    }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) { return 0; }   // TODO(블록 엔티티): 마지막으로 바뀐 칸 + 1
}
