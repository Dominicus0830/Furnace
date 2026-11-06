// 레시피 북으로 격자 채우기 시험: 2×2 모양 레시피를 3×3 격자 가운데(왼쪽 위)에 놓기, 재료 종류가 섞여도 배정, shift 로 최대, 이미 놓인 것 한 벌 더,
// 재료 부족이면 격자를 비우고 고스트, 기존 격자 내용은 인벤토리로 돌아감.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlaceRecipeTest {

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    CraftingMenu table;
    InventoryMenu invMenu;
    final List<String> ghosts = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, rm);
        invMenu = new InventoryMenu(inv, player);
        table = new CraftingMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, () -> true);
        player.known.add(ResourceId.parse("minecraft:crafting_table"));
        player.known.add(ResourceId.parse("minecraft:stick"));
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    void place(RecipeBookMenu menu, String recipe, boolean shift) {
        var rm = player.recipes();
        var entry = rm.craftingEntry(ResourceId.parse(recipe));
        menu.beginPlacingRecipe();
        try {
            new ServerPlaceRecipe(menu, player, inv, (cid, r) -> ghosts.add(r.id().toString())).recipeClicked(entry, shift);
        } finally {
            menu.finishPlacingRecipe(entry);
        }
    }

    String name(ItemStack s) { return s.isEmpty() ? "empty" : ItemTools.itemName(s) + "x" + s.count(); }

    @Test
    void placesTwoByTwoRecipeAtTopLeftOfThreeByThreeGrid() {
        inv.set(PlayerInventory.windowSlot(9), it("oak_planks", 2));
        inv.set(PlayerInventory.windowSlot(10), it("birch_planks", 8));
        place(table, "minecraft:crafting_table", false);
        for (int s : new int[]{1, 2, 4, 5}) assertEquals(1, table.getSlot(s).getItem().count(), "슬롯 " + s);
        assertTrue(table.getSlot(3).getItem().isEmpty());
        assertTrue(table.getSlot(7).getItem().isEmpty());
        assertEquals("minecraft:crafting_table", ItemTools.itemName(table.getSlot(0).getItem()), "결과 칸도 갱신");
        assertTrue(ghosts.isEmpty());
    }

    @Test
    void shiftPlacesBiggestPossibleAndTinyShortageSendsGhost() {
        inv.set(PlayerInventory.windowSlot(9), it("oak_planks", 12));
        place(table, "minecraft:crafting_table", true);
        assertEquals(3, table.getSlot(1).getItem().count(), "12 개 / 4 = 3 벌");
        // 격자의 재료도 센다 — 다시 눌러도 같은 벌 수로 다시 채운다
        place(table, "minecraft:crafting_table", true);
        assertEquals(3, table.getSlot(1).getItem().count());
        // 재료가 없으면 격자를 비우고(있던 건 인벤토리로) 고스트
        for (int s2 : new int[]{1, 2, 4, 5}) table.getSlot(s2).set(ItemStack.EMPTY);
        place(table, "minecraft:crafting_table", true);
        assertEquals(List.of("minecraft:crafting_table"), ghosts);
        assertTrue(table.getSlot(1).getItem().isEmpty());
    }

    @Test
    void clickingAgainAddsOneMoreSet() {
        inv.set(PlayerInventory.windowSlot(9), it("oak_planks", 12));
        place(table, "minecraft:crafting_table", false);
        place(table, "minecraft:crafting_table", false);
        assertEquals(2, table.getSlot(1).getItem().count());
    }

    @Test
    void unknownRecipeDoesNothingAndInventoryMenuUsesTwoByTwoGrid() {
        inv.set(PlayerInventory.windowSlot(9), it("oak_planks", 4));
        place(table, "minecraft:oak_door", false);   // 모르는 레시피
        assertTrue(table.getSlot(1).getItem().isEmpty());
        place(invMenu, "minecraft:crafting_table", false);
        for (int s : new int[]{1, 2, 3, 4}) assertEquals(1, invMenu.getSlot(s).getItem().count());
    }
}
