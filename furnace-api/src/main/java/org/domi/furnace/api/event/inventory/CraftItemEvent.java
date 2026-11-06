// 플레이어가 제작 결과 칸(작업대·인벤토리 2×2)을 클릭해 꺼내기 직전 — InventoryClickEvent 하위라 그 구독자도 받는다.
// 취소하면 꺼내지 않고 창을 다시 보낸다(재료도 줄지 않는다). 결과 칸이 비어 있으면 부르지 않는다(보통 InventoryClickEvent).
package org.domi.furnace.api.event.inventory;

import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;

public final class CraftItemEvent extends InventoryClickEvent {

    private final String recipe;

    public CraftItemEvent(Player player, InventorySnapshot inventory, int slot, int button, String clickType, ItemStack cursor, String recipe) {
        super(player, inventory, slot, button, clickType, cursor);
        this.recipe = recipe;
    }

    /** 쓰인 레시피 id(minecraft:oak_planks 등, 모르면 null). */
    public String recipe() { return recipe; }

    /** 꺼낼 결과(결과 칸 내용). */
    public ItemStack result() { return current(); }
}
