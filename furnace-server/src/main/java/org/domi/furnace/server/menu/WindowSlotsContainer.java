// PlayerInventory 의 창 칸 몇 개를 그대로 컨테이너로 본다 — 제작 격자(창 칸 1..4)와 제작 결과(창 칸 0)에 쓴다.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;

public class WindowSlotsContainer implements Container {

    private final PlayerInventory inv;
    private final int firstWindowSlot, size;

    public WindowSlotsContainer(PlayerInventory inv, int firstWindowSlot, int size) {
        this.inv = inv;
        this.firstWindowSlot = firstWindowSlot;
        this.size = size;
    }

    @Override public int size() { return size; }

    @Override public ItemStack get(int index) { return index >= 0 && index < size ? inv.get(firstWindowSlot + index) : ItemStack.EMPTY; }

    @Override public void set(int index, ItemStack stack) { inv.set(firstWindowSlot + index, stack); }

    @Override public ItemStack remove(int index, int amount) {
        if (index < 0 || index >= size || amount <= 0) return ItemStack.EMPTY;
        int ws = firstWindowSlot + index;
        while (true) {
            ItemStack cur = inv.get(ws);
            if (cur.isEmpty()) return ItemStack.EMPTY;
            int n = Math.min(amount, cur.count());
            if (inv.replace(ws, cur, cur.withCount(cur.count() - n))) return cur.withCount(n);
        }
    }

    @Override public ItemStack removeNoUpdate(int index) {
        ItemStack cur = get(index);
        if (!cur.isEmpty()) inv.set(firstWindowSlot + index, ItemStack.EMPTY);
        return cur;
    }

    @Override public void setChanged() { }

    @Override public boolean stillValid(MenuPlayer player) { return true; }
}
