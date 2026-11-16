package org.domi.furnace.compat.paper;

import org.domi.furnace.api.item.ItemStack;

/** 인벤토리에 붙지 않은 ItemStack 의 값. Bukkit 처럼 개수 0·음수와, 개수 0 인 채 남는 종류를 그대로 들고 있는다. */
final class PlainSlot implements ItemSlot {

    private ItemStack stack;
    private int amount;
    /** 값을 바꿀 때마다 오르는 번호(T-076) — 모든 쓰기는 set·setAmount 를 거친다. */
    private long stamp;

    PlainSlot(ItemStack stack, int amount) {
        this.stack = stack;
        this.amount = amount;
    }

    @Override
    public ItemStack get() { return stack; }

    @Override
    public void set(ItemStack s) {
        stamp++;
        stack = s;
        amount = s.amount();
    }

    @Override
    public int amount() { return amount; }

    @Override
    public long changeStamp() { return stamp; }

    @Override
    public void setAmount(int n) {
        stamp++;
        amount = n;
        stack = new ItemStack(stack.type(), Math.max(0, n), stack.components(), stack.removedComponents());
    }
}
