// 제작 격자가 바뀌어 결과 칸을 새로 정하기 직전(작업대·인벤토리 2×2) — 메뉴를 처리하는 스레드에서 불린다. 맞는 레시피가 없어도 불린다(결과 EMPTY).
// inventory() 는 바뀐 격자를 포함한 창 스냅샷(결과 칸은 아직 이전 값). setResult 로 결과 칸에 둘 아이템을 바꿀 수 있다.
package org.domi.furnace.api.event.inventory;

import java.util.Objects;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;

public final class PrepareItemCraftEvent extends InventoryEvent {

    private final String recipe;
    private ItemStack result;

    public PrepareItemCraftEvent(Player player, InventorySnapshot inventory, String recipe, ItemStack result) {
        super(player, inventory);
        this.recipe = recipe;
        this.result = result;
    }

    /** 맞는 레시피 id(없으면 null). */
    public String recipe() { return recipe; }

    public ItemStack result() { return result; }

    /** EMPTY 면 결과 칸을 비운다. 서버가 모르는 아이템이면 원래 결과를 둔다. */
    public void setResult(ItemStack result) { this.result = Objects.requireNonNull(result, "result"); }
}
