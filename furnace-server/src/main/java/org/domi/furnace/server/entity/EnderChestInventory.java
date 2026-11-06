// 바닐라 PlayerEnderChestContainer 의 저장분: 플레이어마다 27칸. 어느 엔더 상자를 열든 같은 칸들을 본다(playerdata 의 EnderItems).
// 연결 스레드(저장)와 블록 region 스레드(메뉴 클릭)가 함께 쓰므로 메서드마다 잠근다.
package org.domi.furnace.server.entity;

import java.util.Arrays;

public final class EnderChestInventory {

    public static final int SIZE = 27;

    private final ItemStack[] items = new ItemStack[SIZE];

    public EnderChestInventory() { Arrays.fill(items, ItemStack.EMPTY); }

    public synchronized ItemStack get(int i) { return i >= 0 && i < SIZE ? items[i] : ItemStack.EMPTY; }

    public synchronized void set(int i, ItemStack s) { items[i] = s == null ? ItemStack.EMPTY : s; }

    public synchronized ItemStack[] snapshot() { return items.clone(); }

    public synchronized void load(ItemStack[] saved) {
        for (int i = 0; i < SIZE; i++) items[i] = i < saved.length && saved[i] != null ? saved[i] : ItemStack.EMPTY;
    }
}
