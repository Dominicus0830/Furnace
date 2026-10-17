// 바닐라 JukeboxBlock: 음반을 넣으면 재생(has_record). 재생 중이면 신호 15. 꺼내기·재생은 블록 엔티티 동작.
package org.domi.furnace.server.block.types;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockActor;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Hit;
import org.domi.furnace.server.block.InteractionResult;
import org.domi.furnace.server.block.ItemInteractionResult;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class JukeboxBlock extends BaseEntityBlock {

    private static final Prop HAS_RECORD = B.prop("has_record");

    public JukeboxBlock(int block) { super(block); }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Object data = stack.patch().entries().get("minecraft:block_entity_data");
        if (data instanceof NbtCompound c && c.contains("RecordItem")) level.setBlock(pos, HAS_RECORD.with(state, true), 2);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (HAS_RECORD.is(state)) {
            // TODO(블록엔티티): JukeboxBlockEntity.popOutTheItem — 음반을 위로 튀겨 내고 has_record=false
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (HAS_RECORD.is(state)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // TODO(블록엔티티·아이템): JukeboxPlayable.tryInsertIntoJukebox — jukebox_playable 아이템이면 넣고 재생(CONSUME).
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            // TODO(블록엔티티): popOutTheItem
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) {
        return 0;   // TODO(블록엔티티): 재생 중이면 15
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return 0;   // TODO(블록엔티티): 음반의 비교기 출력
    }
}
