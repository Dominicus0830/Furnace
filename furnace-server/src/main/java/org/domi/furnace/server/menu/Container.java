// 바닐라 Container — 슬롯 뒤의 아이템 저장소. ItemStack 이 불변이라 바닐라의 "그 자리에서 count 바꾸기"는 replaceInPlace 로 나타낸다.
package org.domi.furnace.server.menu;

import org.domi.furnace.server.entity.ItemStack;

public interface Container {

    int size();

    ItemStack get(int index);

    /** 바닐라 setItem — 칸 내용을 바꾸고 setChanged 한다. */
    void set(int index, ItemStack stack);

    /** 바닐라 removeItem: 그 칸에서 최대 amount 개를 떼어 돌려준다(칸에는 나머지). 뗀 게 있으면 setChanged. */
    ItemStack remove(int index, int amount);

    /** 바닐라 removeItemNoUpdate: 칸을 통째로 비우고 내용을 돌려준다(setChanged 없음). */
    ItemStack removeNoUpdate(int index);

    void setChanged();

    boolean stillValid(MenuPlayer player);

    /** 바닐라 getMaxStackSize — 컨테이너가 허용하는 칸당 최대 개수. */
    default int maxStackSize() { return 99; }

    /**
     * 바닐라가 칸에 든 ItemStack 의 count 만 그 자리에서 바꿀 때(moveItemStackTo 등)에 해당한다 — setItem 의 부작용
     * (예: 화로의 조리 진행 초기화) 없이 내용만 바꾸고 setChanged 한다. 기본은 set 과 같다.
     */
    default void replaceInPlace(int index, ItemStack stack) { set(index, stack); }

    /** startOpen/stopOpen: 플레이어가 이 컨테이너를 열고 닫는다(상자 뚜껑 열린 사람 수 등). */
    default void startOpen(MenuPlayer player) { }

    default void stopOpen(MenuPlayer player) { }

    /** 움직이는 용기(엔티티)의 지금 블록 좌표 {x, y, z} — 클릭을 그 region 에서 처리한다. 블록 용기·메모리 용기는 null. */
    default int[] containerPos() { return null; }

    default boolean isEmpty() {
        for (int i = 0; i < size(); i++) if (!get(i).isEmpty()) return false;
        return true;
    }
}
