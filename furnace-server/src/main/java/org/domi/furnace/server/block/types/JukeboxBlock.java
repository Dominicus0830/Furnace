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
import org.domi.furnace.server.block.entity.JukeboxBlockEntity;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class JukeboxBlock extends BaseEntityBlock {

    private static final Prop HAS_RECORD = B.prop("has_record");

    public JukeboxBlock(int block) { super(block); }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) { return new JukeboxBlockEntity(pos, state); }

    @Override
    public void setPlacedBy(BlockLevel level, Pos pos, int state, BlockActor placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        Object data = stack.patch().entries().get("minecraft:block_entity_data");
        if (data instanceof NbtCompound c && c.contains("RecordItem")) level.setBlock(pos, HAS_RECORD.with(state, true), 2);
    }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (HAS_RECORD.is(state) && level.getBlockEntity(pos) instanceof JukeboxBlockEntity be) {
            be.popOutTheItem(level);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (HAS_RECORD.is(state)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        // JukeboxPlayable.tryInsertIntoJukebox: jukebox_playable 아이템을 한 장 넣고 재생(통계 play_record)
        if (!(ItemTools.component(stack, "minecraft:jukebox_playable") instanceof java.util.Map<?, ?>)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        ItemStack one = stack.withCount(1);
        if (!player.isCreative()) player.setItemInHand(hand, stack.withCount(stack.count() - 1));
        if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity be) be.insert(one, player, level);
        player.awardStat("minecraft:play_record");
        return ItemInteractionResult.CONSUME;
    }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            if (level.getBlockEntity(pos) instanceof JukeboxBlockEntity be) be.popOutTheItem(level);
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) {
        return level.getBlockEntity(pos) instanceof JukeboxBlockEntity be && be.isPlaying() ? 15 : 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return level.getBlockEntity(pos) instanceof JukeboxBlockEntity be ? be.comparatorOutput() : 0;
    }
}
