// 지도 제작대 시험: 슬롯 허용, 지도 저장 데이터가 없으면 결과 없음(바닐라와 같다), 데이터가 있으면 확대·잠금·복제 결과.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CartographyMenuTest {

    CartographyCtx c;
    BlockAccess.MapInfo info;
    CartographyMenu menu;
    CraftingMenuTest.Player player;

    record CartographyCtx() { }

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        menu = new CartographyMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 1; }
            @Override public void playSound(String s) { }
            @Override public MapInfo mapInfo(ItemStack map) { return info; }
        });
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    @Test
    void noSavedDataGivesNoResult() {
        info = null;
        menu.getSlot(0).set(it("filled_map", 1));
        menu.getSlot(1).set(it("paper", 1));
        assertTrue(menu.getSlot(CartographyMenu.RESULT_SLOT).getItem().isEmpty());
    }

    @Test
    void paperScalesGlassLocksEmptyMapClones() {
        info = new BlockAccess.MapInfo(false, 1);
        menu.getSlot(0).set(it("filled_map", 1));
        menu.getSlot(1).set(it("paper", 2));
        ItemStack out = menu.getSlot(CartographyMenu.RESULT_SLOT).getItem();
        assertEquals("minecraft:filled_map", ItemTools.itemName(out));
        assertEquals("scale", ItemTools.component(out, "minecraft:map_post_processing"));
        menu.getSlot(1).set(it("glass_pane", 1));
        assertEquals("lock", ItemTools.component(menu.getSlot(CartographyMenu.RESULT_SLOT).getItem(), "minecraft:map_post_processing"));
        menu.getSlot(1).set(it("map", 1));
        assertEquals(2, menu.getSlot(CartographyMenu.RESULT_SLOT).getItem().count());
        menu.clicked(CartographyMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals(2, menu.getCarried().count());
        assertTrue(menu.getSlot(0).getItem().isEmpty());
        assertTrue(menu.getSlot(1).getItem().isEmpty());
    }

    @Test
    void maxScaleOrLockedRejectsPaperAndGlass() {
        info = new BlockAccess.MapInfo(false, 4);
        menu.getSlot(0).set(it("filled_map", 1));
        menu.getSlot(1).set(it("paper", 1));
        assertTrue(menu.getSlot(CartographyMenu.RESULT_SLOT).getItem().isEmpty());
        info = new BlockAccess.MapInfo(true, 0);
        menu.getSlot(1).set(it("glass_pane", 1));
        assertTrue(menu.getSlot(CartographyMenu.RESULT_SLOT).getItem().isEmpty());
    }

    @Test
    void slotsAcceptOnlyTheirKind() {
        assertTrue(menu.getSlot(0).mayPlace(it("filled_map", 1)));
        assertFalse(menu.getSlot(0).mayPlace(it("map", 1)));
        assertTrue(menu.getSlot(1).mayPlace(it("paper", 1)));
        assertTrue(menu.getSlot(1).mayPlace(it("glass_pane", 1)));
        assertFalse(menu.getSlot(1).mayPlace(it("dirt", 1)));
    }
}
