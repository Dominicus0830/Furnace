// 바닐라 ChiseledBookShelfBlockEntity: 책 6칸(칸당 1권, #bookshelf_books 만)과 마지막으로 바뀐 칸(비교기 출력 = 칸 + 1).
// 칸이 바뀌면 slot_N_occupied 상태를 모두 다시 맞추고(setBlock 3) block_change 게임 이벤트. 저장은 Items(항상)·last_interacted_slot.
// 깔때기에는 Container 로 보인다(최대 수량 1, 넣기는 빈 칸에 책 한 권, 빼기는 받는 쪽에 자리가 있을 때).
package org.domi.furnace.server.block.entity;

import java.util.Arrays;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.net.io.nbt.NbtCompound;

public class ChiseledBookShelfBlockEntity extends ContainerBlockEntity {

    public static final int MAX_BOOKS_IN_STORAGE = 6;
    private static final Prop[] SLOT_OCCUPIED = {B.prop("slot_0_occupied"), B.prop("slot_1_occupied"), B.prop("slot_2_occupied"),
            B.prop("slot_3_occupied"), B.prop("slot_4_occupied"), B.prop("slot_5_occupied")};
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(ChiseledBookShelfBlockEntity.class);

    private int lastInteractedSlot = -1;

    public ChiseledBookShelfBlockEntity(Pos pos, int state) { super("minecraft:chiseled_bookshelf", pos, state, MAX_BOOKS_IN_STORAGE); }

    @Override public boolean isValid(int state) { return B.is(state, "chiseled_bookshelf"); }

    @Override public String defaultTitleKey() { return "block.minecraft.chiseled_bookshelf"; }

    public int lastInteractedSlot() { return lastInteractedSlot; }

    public static boolean isBook(ItemStack s) { return B.holdsTag(s, "minecraft:bookshelf_books"); }

    /** updateState(slot): 칸 점유 상태를 다시 맞추고 알린다. */
    private void updateState(int slot, BlockLevel level) {
        if (slot < 0 || slot >= 6) {
            LOG.error("Expected slot 0-5, got {}", slot);
            return;
        }
        lastInteractedSlot = slot;
        int s = blockState;
        for (int i = 0; i < SLOT_OCCUPIED.length; i++) s = SLOT_OCCUPIED[i].with(s, !items[i].isEmpty());
        level.setBlock(pos, s, 3);
        level.gameEvent("minecraft:block_change", pos, null, s);
    }

    public int count() {
        int n = 0;
        for (ItemStack s : items) if (!s.isEmpty()) n++;
        return n;
    }

    public void clearContent() { Arrays.fill(items, ItemStack.EMPTY); }

    public boolean isEmpty() {
        for (ItemStack s : items) if (!s.isEmpty()) return false;
        return true;
    }

    public ItemStack getItem(int slot) { return items[slot]; }

    /** removeItem(slot, n): 수와 관계없이 칸을 통째로 비운다(책이 있었으면 상태 갱신). */
    public ItemStack removeItem(int slot, int n, BlockLevel level) {
        ItemStack s = items[slot] == null ? ItemStack.EMPTY : items[slot];
        items[slot] = ItemStack.EMPTY;
        if (!s.isEmpty()) updateState(slot, level);
        return s;
    }

    /** setItem: 책이면 넣고 상태 갱신, 빈 스택이면 removeItem(slot, 1), 그 밖에는 무시. */
    public void setItem(int slot, ItemStack s, BlockLevel level) {
        if (isBook(s)) {
            items[slot] = s;
            updateState(slot, level);
        } else if (s.isEmpty()) {
            removeItem(slot, 1, level);
        }
    }

    /** canPlaceItem: 책이고 칸이 비었고 수가 최대 수량(1). */
    public boolean canPlaceItem(int slot, ItemStack s) { return isBook(s) && items[slot].isEmpty() && s.count() == 1; }

    @Override
    public void load(NbtCompound tag) {
        NbtCompound t = tag.copy();
        t.tags().remove("CustomName");
        t.tags().remove("Lock");
        t.tags().remove("LootTable");
        super.load(t);
        lastInteractedSlot = tag.getIntOr0("last_interacted_slot");   // getInt: 없으면 0
    }

    @Override
    protected void saveAdditional(NbtCompound tag) {
        String ln = lootTable, cn = customName, lk = lock;
        lootTable = null;
        customName = null;
        lock = null;
        super.saveAdditional(tag);
        lootTable = ln;
        customName = cn;
        lock = lk;
        tag.putInt("last_interacted_slot", lastInteractedSlot);
    }
}
