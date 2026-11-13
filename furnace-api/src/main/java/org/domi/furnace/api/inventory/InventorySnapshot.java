// 이벤트 시점의 인벤토리 내용 — 불변. 종류는 메뉴 종류 키(minecraft:player, minecraft:crafting …).
// 서버는 칸의 (불변) 아이템 참조를 이벤트 시점에 복사한 LazyList 를 넘긴다 — 칸을 API 아이템으로 바꾸는 일은 처음 읽을 때 칸마다 한다(T-033).
// LazyList 는 List.copyOf 결과와 관측 결과가 같은 불변 목록이라 그대로 받고, 그 밖의 목록은 예전처럼 List.copyOf 로 복사한다.
package org.domi.furnace.api.inventory;

import java.util.List;
import org.domi.furnace.api.event.LazyList;
import org.domi.furnace.api.item.ItemStack;

public record InventorySnapshot(String type, List<ItemStack> contents) {

    public InventorySnapshot {
        contents = contents instanceof LazyList<ItemStack> lazy ? lazy : List.copyOf(contents);
    }

    public int size() { return contents.size(); }

    /** 범위 밖이면 EMPTY. */
    public ItemStack get(int slot) { return slot >= 0 && slot < contents.size() ? contents.get(slot) : ItemStack.EMPTY; }
}
