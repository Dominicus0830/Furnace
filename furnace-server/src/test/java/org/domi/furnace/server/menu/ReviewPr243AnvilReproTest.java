// PR #243 검토 재현: 최대 비용을 낮췄을 때 이름만 바꾸기 결과의 비용이 0 이 되는데, 0 비용 결과는 꺼낼 수 없다(CraftBukkit 은 0 도 허용).
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReviewPr243AnvilReproTest {
    AnvilMenuTest.P player;
    AnvilMenu menu;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        player = new AnvilMenuTest.P(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        menu = new AnvilMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { }
            @Override public void anvilUsed(boolean inf) { }
        });
    }

    @Test
    void zeroCostResultCanBeTaken() {   // Paper(CraftBukkit): mayPickup = cost > DEFAULT_DENIED_COST(-1) && hasItem — "allow cost 0 like a free item"
        menu.getSlot(0).set(ItemTools.with(AnvilMenuTest.it("iron_sword", 1), "minecraft:repair_cost", 0));
        menu.setMaximumRepairCost(1);
        menu.setItemName("Foo");
        assertEquals(0, menu.cost());
        assertFalse(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty());
        assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player), "비용 0 결과는 꺼낼 수 있어야 한다(무료 아이템)");
    }

    @Test
    void emptyResultStillCannotBeTakenAtCostZero() {   // 고치더라도(>= 0) 빈 결과는 막아야 한다(hasItem)
        menu.getSlot(0).set(AnvilMenuTest.it("iron_sword", 1));
        menu.getSlot(1).set(AnvilMenuTest.it("stick", 1));   // 맞지 않는 재료 → empty()
        assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty());
        assertFalse(menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player));
    }

    @Test
    void maximumZeroMakesRenameCostMinusOne() {   // 한계 0: 이름만 바꾸면 비용이 −1 로 눌린다(Paper 도 같다) — 기록용
        menu.getSlot(0).set(AnvilMenuTest.it("iron_sword", 1));
        menu.setMaximumRepairCost(0);
        menu.setItemName("Foo");
        System.out.println("max=0 rename cost=" + menu.cost() + " resultEmpty=" + menu.getSlot(AnvilMenu.RESULT_SLOT).getItem().isEmpty());
    }
}
