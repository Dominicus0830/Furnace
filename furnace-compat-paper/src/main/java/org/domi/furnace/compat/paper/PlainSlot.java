package org.domi.furnace.compat.paper;

import org.domi.furnace.api.item.ItemStack;

/** 인벤토리에 붙지 않은 ItemStack 의 값. Bukkit 처럼 개수 0·음수와, 개수 0 인 채 남는 종류를 그대로 들고 있는다. */
final class PlainSlot implements ItemSlot {

    private ItemStack stack;
    private int amount;

    PlainSlot(ItemStack stack, int amount) {
        this.stack = stack;
        this.amount = amount;
    }

    @Override
    public ItemStack get() { return stack; }

    @Override
    public void set(ItemStack s) {
        stack = s;
        amount = s.amount();
    }

    @Override
    public int amount() { return amount; }

    @Override
    public void setAmount(int n) {
        amount = n;
        stack = new ItemStack(stack.type(), Math.max(0, n), stack.components(), stack.removedComponents());
    }
}
