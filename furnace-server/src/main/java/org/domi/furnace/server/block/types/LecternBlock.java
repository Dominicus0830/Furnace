// 바닐라 LecternBlock: 책을 올려 두는 독서대. 책장을 넘기면 2틱 동안 신호(powered). 비교기는 쪽 번호에 따른 세기.
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
import org.domi.furnace.server.block.RandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class LecternBlock extends BaseEntityBlock {

    static final Prop FACING = B.prop("facing"), POWERED = B.prop("powered"), HAS_BOOK = B.prop("has_book");

    public LecternBlock(int block) { super(block); }

    @Override
    public int getStateForPlacement(PlaceContext ctx) {
        boolean book = false;
        BlockActor player = ctx.player();
        if (player != null && player.canUseGameMasterBlocks()) {
            Object data = ctx.itemInHand().patch().entries().get("minecraft:block_entity_data");
            if (data instanceof NbtCompound c && c.contains("Book")) book = true;
        }
        int s = FACING.with(defaultState(), Dir.NAME[Dir.opposite(ctx.horizontalDirection())]);
        return HAS_BOOK.with(s, book);
    }

    /** tryPlaceBook: 책이 없으면 올린다(블록 엔티티에 책 1권). */
    public static boolean tryPlaceBook(BlockActor entity, BlockLevel level, Pos pos, int state, int hand, ItemStack book) {
        if (HAS_BOOK.is(state)) return false;
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.LecternBlockEntity be) {
            be.setBook(book.withCount(1), level);   // ItemStack.consumeAndReturn(1, entity): 만들기 모드가 아니면 손에서 하나 줄인다
            if (!entity.isCreative()) entity.setItemInHand(hand, book.withCount(book.count() - 1));
        }
        resetBookState(entity, level, pos, state, true);
        level.playSound(null, pos, "minecraft:item.book.put", "blocks", 1.0F, 1.0F);
        return true;
    }

    public static void resetBookState(BlockActor entity, BlockLevel level, Pos pos, int state, boolean hasBook) {
        int s = HAS_BOOK.with(POWERED.with(state, false), hasBook);
        level.setBlock(pos, s, 3);
        level.gameEvent("minecraft:block_change", pos, entity, s);
        updateBelow(level, pos, state);
    }

    /** signalPageChange: 2틱 동안 신호 + 책장 넘기는 소리 이벤트(1043). */
    public static void signalPageChange(BlockLevel level, Pos pos, int state) {
        changePowered(level, pos, state, true);
        level.scheduleTick(pos, B.block(state), 2);
        level.levelEvent(null, 1043, pos, 0);
    }

    /** popBook: 책이 있으면 앞쪽 0.25 · 위 1.0 에 아이템 엔티티로 떨구고(속도는 아이템 엔티티 생성자의 레벨 난수 2번) 블록 엔티티를 비운다. */
    private static void popBook(int state, BlockLevel level, Pos pos) {
        if (level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.LecternBlockEntity be && be.hasBook()) {
            int dir = Dir.byName(FACING.valueName(state));
            ItemStack book = be.book();
            float dx = 0.25F * Dir.DX[dir];
            float dz = 0.25F * Dir.DZ[dir];
            level.spawnItemEntity(pos.x() + 0.5 + (double) dx, (double) (pos.y() + 1), pos.z() + 0.5 + (double) dz, book);
            be.setBook(ItemStack.EMPTY, null);
        }
    }

    private static void changePowered(BlockLevel level, Pos pos, int state, boolean powered) {
        level.setBlock(pos, POWERED.with(state, powered), 3);
        updateBelow(level, pos, state);
    }

    private static void updateBelow(BlockLevel level, Pos pos, int state) { level.updateNeighborsAt(pos.below(), B.block(state)); }

    @Override
    public void tick(int state, BlockLevel level, Pos pos, RandomSource random) { changePowered(level, pos, state, false); }

    @Override
    public void onRemove(int state, BlockLevel level, Pos pos, int newState, boolean moved) {
        if (!B.is(newState, block)) {
            popBook(state, level, pos);
            super.onRemove(state, level, pos, newState, moved);
            if (POWERED.is(state)) level.updateNeighborsAt(pos.below(), block);
        }
    }

    @Override
    public boolean isSignalSource(int state) { return true; }

    @Override
    public int getSignal(int state, BlockLevel level, Pos pos, int dir) { return POWERED.is(state) ? 15 : 0; }

    @Override
    public int getDirectSignal(int state, BlockLevel level, Pos pos, int dir) { return dir == Dir.UP && POWERED.is(state) ? 15 : 0; }

    @Override
    public boolean hasAnalogOutputSignal(int state) { return true; }

    @Override
    public int getAnalogOutputSignal(int state, BlockLevel level, Pos pos) {
        return HAS_BOOK.is(state) && level.getBlockEntity(pos) instanceof org.domi.furnace.server.block.entity.LecternBlockEntity be ? be.redstoneSignal() : 0;
    }

    @Override
    public ItemInteractionResult useItemOn(ItemStack stack, int state, BlockLevel level, Pos pos, BlockActor player, int hand, Hit hit) {
        if (HAS_BOOK.is(state)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (isLecternBook(stack)) {
            return tryPlaceBook(player, level, pos, state, hand, stack) ? ItemInteractionResult.CONSUME : ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        return stack.isEmpty() && hand == BlockActor.MAIN_HAND
                ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    private static boolean isLecternBook(ItemStack s) { return B.holdsTag(s, "minecraft:lectern_books"); }

    @Override
    public InteractionResult useWithoutItem(int state, BlockLevel level, Pos pos, BlockActor player, Hit hit) {
        if (HAS_BOOK.is(state)) {
            player.openContainer(pos);
            player.awardStat("minecraft:interact_with_lectern");
            return InteractionResult.CONSUME;
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public org.domi.furnace.server.block.entity.BlockEntity newBlockEntity(Pos pos, int state) {
        return new org.domi.furnace.server.block.entity.LecternBlockEntity(pos, state);
    }
}
