// 베틀 시험: 무늬 목록(무늬 아이템 없음/있음), 고르기 → 결과(층 추가), 꺼내기(깃발·염료 1개 소모, 무늬 아이템은 그대로), 6층이면 결과 없음.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoomMenuTest {

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    LoomMenu menu;
    int sounds;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        menu = new LoomMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 5; }
            @Override public void playSound(String s) { sounds++; }
        });
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    @Test
    void withoutPatternItemListsBasicPatternsAndAppliesChosenOne() {
        menu.getSlot(0).set(it("white_banner", 2));
        menu.getSlot(1).set(it("red_dye", 2));
        List<String> list = menu.selectablePatterns();
        assertEquals(34, list.size());
        assertEquals("minecraft:square_bottom_left", list.get(0));
        assertEquals(-1, menu.selectedBannerPatternIndex());
        assertTrue(menu.clickMenuButton(player, 4));
        assertFalse(menu.clickMenuButton(player, 99));
        ItemStack out = menu.getSlot(LoomMenu.RESULT_SLOT).getItem();
        assertEquals("minecraft:white_banner", ItemTools.itemName(out));
        assertEquals(List.of(Map.of("pattern", "minecraft:stripe_bottom", "color", "red")), ItemTools.component(out, "minecraft:banner_patterns"));
        menu.clicked(LoomMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals(1, menu.getSlot(0).getItem().count());
        assertEquals(1, menu.getSlot(1).getItem().count());
        assertEquals(1, sounds);
        assertEquals(1, menu.getCarried().count());
    }

    @Test
    void patternItemRestrictsToItsOwnPatternAndIsNotConsumed() {
        menu.getSlot(0).set(it("white_banner", 1));
        menu.getSlot(1).set(it("black_dye", 1));
        menu.getSlot(2).set(it("flower_banner_pattern", 1));
        assertEquals(List.of("minecraft:flower"), menu.selectablePatterns());
        assertEquals(0, menu.selectedBannerPatternIndex());
        assertFalse(menu.getSlot(LoomMenu.RESULT_SLOT).getItem().isEmpty());
        menu.clicked(LoomMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals("minecraft:flower_banner_pattern", ItemTools.itemName(menu.getSlot(2).getItem()));
        assertTrue(menu.getSlot(0).getItem().isEmpty());
    }

    @Test
    void sixLayersGiveNoResult() {
        ItemStack b = ItemTools.with(it("white_banner", 1), "minecraft:banner_patterns",
                List.of(Map.of("pattern", "minecraft:cross", "color", "red"), Map.of("pattern", "minecraft:cross", "color", "red"),
                        Map.of("pattern", "minecraft:cross", "color", "red"), Map.of("pattern", "minecraft:cross", "color", "red"),
                        Map.of("pattern", "minecraft:cross", "color", "red"), Map.of("pattern", "minecraft:cross", "color", "red")));
        menu.getSlot(0).set(b);
        menu.getSlot(1).set(it("red_dye", 1));
        menu.getSlot(2).set(it("flower_banner_pattern", 1));
        assertTrue(menu.getSlot(LoomMenu.RESULT_SLOT).getItem().isEmpty());
        assertEquals(-1, menu.selectedBannerPatternIndex());
    }

    @Test
    void slotsAcceptOnlyTheirKind() {
        assertTrue(menu.getSlot(0).mayPlace(it("red_banner", 1)));
        assertFalse(menu.getSlot(0).mayPlace(it("red_dye", 1)));
        assertTrue(menu.getSlot(1).mayPlace(it("red_dye", 1)));
        assertTrue(menu.getSlot(2).mayPlace(it("creeper_banner_pattern", 1)));
        assertFalse(menu.getSlot(2).mayPlace(it("paper", 1)));
    }
}
