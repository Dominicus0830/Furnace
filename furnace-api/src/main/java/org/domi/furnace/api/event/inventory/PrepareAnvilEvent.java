// 모루의 결과를 정한 직후(재료·이름이 바뀔 때마다) — 메뉴를 처리하는 스레드에서 불린다. 결과가 없어도(EMPTY) 불린다.
// setResult 로 결과 칸에 둘 아이템을, setRepairCost 로 필요한 레벨을 바꿀 수 있다(40 이상이어도 그대로 가져갈 수 있는지는 서버 규칙을 따른다).
package org.domi.furnace.api.event.inventory;

import java.util.Objects;
import org.domi.furnace.api.entity.Player;
import org.domi.furnace.api.inventory.InventorySnapshot;
import org.domi.furnace.api.item.ItemStack;

public final class PrepareAnvilEvent extends InventoryEvent {

    private final String renameText;
    private ItemStack result;
    private int repairCost;

    public PrepareAnvilEvent(Player player, InventorySnapshot inventory, ItemStack result, int repairCost, String renameText) {
        super(player, inventory);
        this.result = result;
        this.repairCost = repairCost;
        this.renameText = renameText;
    }

    public ItemStack result() { return result; }

    /** EMPTY 면 결과 칸을 비운다. 서버가 모르는 아이템이면 원래 결과를 둔다. */
    public void setResult(ItemStack result) { this.result = Objects.requireNonNull(result, "result"); }

    /** 결과를 꺼내는 데 드는 경험치 레벨. */
    public int repairCost() { return repairCost; }

    public void setRepairCost(int repairCost) { this.repairCost = repairCost; }

    /** 이름 입력란의 글자(없으면 null). */
    public String renameText() { return renameText; }
}
