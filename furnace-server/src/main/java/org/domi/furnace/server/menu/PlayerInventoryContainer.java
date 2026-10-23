// 플레이어 인벤토리를 Container 로 보는 창 — 바닐라 Inventory(0..8 핫바, 9..35 주, 36..39 발·다리·가슴·머리, 40 왼손).
// 저장은 PlayerInventory 의 창 칸 배열에 그대로 둔다(저장 형식·다른 코드가 그 배열을 쓴다).
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;

public class PlayerInventoryContainer implements Container {

    public static final int SIZE = 41;

    private final PlayerInventory inv;

    public PlayerInventoryContainer(PlayerInventory inv) { this.inv = inv; }

    private static int window(int index) { return PlayerInventory.windowSlot(index); }

    @Override public int size() { return SIZE; }

    @Override public ItemStack get(int index) { return index >= 0 && index < SIZE ? inv.get(window(index)) : ItemStack.EMPTY; }

    @Override public void set(int index, ItemStack stack) { inv.set(window(index), stack); }

    @Override public ItemStack remove(int index, int amount) {
        if (index < 0 || index >= SIZE || amount <= 0) return ItemStack.EMPTY;
        int ws = window(index);
        while (true) {
            ItemStack cur = inv.get(ws);
            if (cur.isEmpty()) return ItemStack.EMPTY;
            int n = Math.min(amount, cur.count());
            if (inv.replace(ws, cur, cur.withCount(cur.count() - n))) return cur.withCount(n);
        }
    }

    @Override public ItemStack removeNoUpdate(int index) {
        ItemStack cur = get(index);
        if (!cur.isEmpty()) inv.set(window(index), ItemStack.EMPTY);
        return cur;
    }

    @Override public void setChanged() { }

    @Override public boolean stillValid(MenuPlayer player) { return true; }
}
