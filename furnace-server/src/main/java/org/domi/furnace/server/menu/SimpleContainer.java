// 바닐라 SimpleContainer — 배열 하나로 된 컨테이너.
package org.domi.furnace.server.menu;

import java.util.Arrays;
import org.domi.furnace.server.entity.ItemStack;

public class SimpleContainer implements Container {

    private final ItemStack[] items;

    public SimpleContainer(int size) {
        items = new ItemStack[size];
        Arrays.fill(items, ItemStack.EMPTY);
    }

    @Override public int size() { return items.length; }

    @Override public ItemStack get(int index) { return index >= 0 && index < items.length ? items[index] : ItemStack.EMPTY; }

    @Override public void set(int index, ItemStack stack) {
        items[index] = stack == null ? ItemStack.EMPTY : stack;
        setChanged();
    }

    @Override public ItemStack remove(int index, int amount) {
        ItemStack cur = get(index);
        if (cur.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        int n = Math.min(amount, cur.count());
        items[index] = cur.withCount(cur.count() - n);
        setChanged();
        return cur.withCount(n);
    }

    @Override public ItemStack removeNoUpdate(int index) {
        ItemStack cur = get(index);
        if (index >= 0 && index < items.length) items[index] = ItemStack.EMPTY;
        return cur;
    }

    @Override public void setChanged() { }

    @Override public boolean stillValid(MenuPlayer player) { return true; }
}
