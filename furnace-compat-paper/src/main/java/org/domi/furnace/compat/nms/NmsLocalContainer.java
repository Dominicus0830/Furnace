package org.domi.furnace.compat.nms;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** 서버와 이어지지 않은 제 칸의 컨테이너(바닐라 생성자로 만든 인벤토리 창의 제작 칸). 스택 객체를 그대로 든다. */
public final class NmsLocalContainer implements Container {

    private final ItemStack[] slots;

    public NmsLocalContainer(int size) {
        slots = new ItemStack[size];
        java.util.Arrays.fill(slots, ItemStack.EMPTY);
    }

    @Override
    public int getContainerSize() { return slots.length; }

    @Override
    public synchronized boolean isEmpty() {
        for (ItemStack s : slots) if (!s.isEmpty()) return false;
        return true;
    }

    @Override
    public synchronized ItemStack getItem(int slot) { return slot >= 0 && slot < slots.length ? slots[slot] : ItemStack.EMPTY; }

    @Override
    public synchronized ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= slots.length || amount <= 0 || slots[slot].isEmpty()) return ItemStack.EMPTY;
        return slots[slot].split(amount);
    }

    @Override
    public synchronized ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= slots.length) return ItemStack.EMPTY;
        ItemStack s = slots[slot];
        slots[slot] = ItemStack.EMPTY;
        return s;
    }

    @Override
    public synchronized void setItem(int slot, ItemStack stack) {
        if (slot >= 0 && slot < slots.length) slots[slot] = java.util.Objects.requireNonNull(stack, "stack");
    }

    @Override
    public void setChanged() {}

    @Override
    public boolean stillValid(Player player) { return true; }

    @Override
    public synchronized void clearContent() { java.util.Arrays.fill(slots, ItemStack.EMPTY); }
}
