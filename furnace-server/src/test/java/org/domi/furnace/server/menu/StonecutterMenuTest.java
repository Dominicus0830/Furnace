// 석재 절단기 시험: 재료에 맞는 레시피 목록(번역 키 순), 고르기 → 결과 칸, 꺼내기(재료 1개 소모·레시피 해금·소리 한 번), shift-클릭, 닫을 때 재료 돌려주기.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StonecutterMenuTest {

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    InventoryMenu invMenu;
    StonecutterMenu menu;
    final List<String> sounds = new ArrayList<>();
    long time = 100;

    @BeforeEach
    void setUp() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, rm);
        invMenu = new InventoryMenu(inv, player);
        menu = new StonecutterMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return time; }
            @Override public void playSound(String s) { sounds.add(s); }
        });
    }

    static ItemStack it(String name, int n) { return TestRecipes.item(name, n); }

    String name(ItemStack s) { return s.isEmpty() ? "empty" : org.domi.furnace.server.item.ItemContext.name("minecraft:item", s.itemId()) + "x" + s.count(); }

    void put(String item, int n) {
        menu.getSlot(StonecutterMenu.INPUT_SLOT).set(it(item, n));
    }

    @Test
    void recipeListIsSortedByDescriptionIdAndChoosingFillsResult() {
        put("stone", 5);
        var names = new ArrayList<String>();
        for (var e : menu.recipes()) names.add(e.id().path());
        assertTrue(names.size() >= 5, names.toString());
        assertEquals("chiseled_stone_bricks_stone_from_stonecutting", names.get(0));
        assertEquals(-1, menu.selectedRecipeIndex());
        assertTrue(menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem().isEmpty());
        int slab = names.indexOf("stone_slab_from_stone_stonecutting");
        assertTrue(menu.clickMenuButton(player, slab));
        assertEquals("minecraft:stone_slabx2", name(menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem()));
        assertTrue(menu.clickMenuButton(player, 999), "범위 밖 번호도 true 지만 아무것도 바꾸지 않는다");
        assertEquals(slab, menu.selectedRecipeIndex());
    }

    @Test
    void takingResultConsumesOneInputUnlocksRecipeAndPlaysSoundOncePerTick() {
        put("stone", 2);
        int slab = -1;
        for (int i = 0; i < menu.recipes().size(); i++) if (menu.recipes().get(i).id().path().equals("stone_slab_from_stone_stonecutting")) slab = i;
        menu.clickMenuButton(player, slab);
        menu.clicked(StonecutterMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals("minecraft:stone_slabx2", name(menu.getCarried()));
        assertEquals(1, menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem().count());
        assertEquals("minecraft:stone_slabx2", name(menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem()), "재료가 남아 있으면 결과가 다시 채워진다");
        assertEquals(List.of("minecraft:ui.stonecutter.take_result"), sounds);
        assertEquals(List.of(ResourceId.parse("minecraft:stone_slab_from_stone_stonecutting")), player.awarded);
        menu.clicked(StonecutterMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);   // 같은 틱: 소리는 다시 나지 않는다
        assertEquals(1, sounds.size());
        assertEquals("minecraft:stone_slabx4", name(menu.getCarried()));
        assertTrue(menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem().isEmpty());
        assertTrue(menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem().isEmpty(), "재료가 떨어지면 결과도 비고 목록이 지워진다");
        assertTrue(menu.recipes().isEmpty());
        assertEquals(-1, menu.selectedRecipeIndex());
    }

    @Test
    void quickMoveSendsMatchingItemsToInputAndResultToInventory() {
        inv.set(PlayerInventory.windowSlot(9), it("stone", 3));
        int invSlot = menu.findSlot(invMenu.inventoryContainer(), 9);
        menu.clicked(invSlot, 0, ClickType.QUICK_MOVE, player);
        assertEquals("minecraft:stonex3", name(menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem()));
        menu.clickMenuButton(player, 0);
        menu.clicked(StonecutterMenu.RESULT_SLOT, 0, ClickType.QUICK_MOVE, player);
        assertFalse(menu.getSlot(StonecutterMenu.INPUT_SLOT).hasItem() && menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem().count() == 3);
        assertTrue(inv.contains(menu.getSlot(StonecutterMenu.RESULT_SLOT).getItem().isEmpty() ? it("chiseled_stone_bricks", 1) : it("stone", 1)));
    }

    @Test
    void closingReturnsInput() {
        put("stone", 4);
        menu.removed(player);
        assertTrue(inv.contains(it("stone", 1)));
        assertTrue(menu.getSlot(StonecutterMenu.INPUT_SLOT).getItem().isEmpty());
    }
}
