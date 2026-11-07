package org.domi.furnace.compat.paper;

import org.domi.furnace.api.item.ItemStack;

/**
 * Bukkit ItemStack 이 비추는 칸 하나(호환 모듈 전용). 평범한 ItemStack 은 값을 자기가 들고 있고,
 * 인벤토리에서 꺼낸 ItemStack 은 그 칸을 그대로 읽고 쓴다(Bukkit 의 "거울" 스택).
 */
public interface ItemSlot {

    /** 지금 값(빈 칸이면 EMPTY). */
    ItemStack get();

    void set(ItemStack stack);

    /** Bukkit 은 개수 0·음수 스택을 들고 있을 수 있다. 칸 거울은 get().amount() 와 같다. */
    default int amount() { return get().amount(); }

    default void setAmount(int n) { set(get().withAmount(n)); }
}
