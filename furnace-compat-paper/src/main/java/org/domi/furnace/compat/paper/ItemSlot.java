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

    /**
     * 변경 번호(T-076). 이 칸의 값을 바꾸는 모든 메서드({@link #set}·{@link #setAmount})가 올린다 — 같은 칸에서 번호가 같으면 값이 같다.
     * 값을 스스로 들고 있는 칸(평범한 스택)만 추적하고, 그 밖의 칸(인벤토리 거울)은 -1(추적 안 함 — 늘 값을 직접 비교).
     */
    default long changeStamp() { return -1; }
}
