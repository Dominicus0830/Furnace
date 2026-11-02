// 이벤트 시점의 인벤토리 내용 — 불변. 종류는 메뉴 종류 키(minecraft:player, minecraft:crafting …).
package org.domi.furnace.api.inventory;

import java.util.List;
import org.domi.furnace.api.item.ItemStack;

public record InventorySnapshot(String type, List<ItemStack> contents) {

    public InventorySnapshot {
        contents = List.copyOf(contents);
    }

    public int size() { return contents.size(); }

    /** 범위 밖이면 EMPTY. */
    public ItemStack get(int slot) { return slot >= 0 && slot < contents.size() ? contents.get(slot) : ItemStack.EMPTY; }
}
