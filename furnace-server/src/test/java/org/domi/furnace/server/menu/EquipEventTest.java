// 장비 칸(방어구)을 플레이어가 채우거나 비울 때 onEquipItem 이 불린다: 같은 아이템이면 부르지 않고(소리 없음) 바뀌면 부른다.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.Test;

class EquipEventTest {

    @Test
    void armorSlotReportsEquipChanges() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        List<String> events = new ArrayList<>();
        var player = new CraftingMenuTest.Player(inv, TestRecipes.manager()) {
            @Override public EquipSlot equipmentSlotFor(ItemStack s) { return EquipSlot.HEAD; }
            @Override public void onEquipItem(EquipSlot slot, ItemStack o, ItemStack n) { events.add(slot + ":" + o.count() + "->" + n.count()); }
        };
        var menu = new InventoryMenu(inv, player);
        inv.set(PlayerInventory.windowSlot(0), TestRecipes.item("iron_helmet", 1));
        menu.clicked(36, 0, ClickType.PICKUP, player);   // 핫바 0 의 투구를 집고
        menu.clicked(5, 0, ClickType.PICKUP, player);    // 머리 칸에 놓는다
        assertEquals(List.of("HEAD:0->1"), events);
        menu.clicked(5, 0, ClickType.PICKUP, player);    // 다시 집으면 비워진다
        assertEquals(List.of("HEAD:0->1", "HEAD:1->0"), events);
    }
}
