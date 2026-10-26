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

    @org.junit.jupiter.api.io.TempDir java.nio.file.Path world;

    @Test
    void takingScaleOrLockResultMakesANewMapId() {
        org.domi.furnace.server.map.MapStore.install(world);
        try {
            ItemStack base = org.domi.furnace.server.map.MapItems.create("minecraft:overworld", 0, 0, (byte) 1, true, false);
            int baseId = org.domi.furnace.server.map.MapItems.mapId(base);
            var data = org.domi.furnace.server.map.MapStore.of(base);
            info = new BlockAccess.MapInfo(data.locked(), data.scale());
            // 종이: 확대 — 꺼내면 배율 2 의 새 번호, 처리 표시는 떨어진다
            menu.getSlot(0).set(base);
            menu.getSlot(1).set(it("paper", 1));
            menu.clicked(CartographyMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
            ItemStack scaled = menu.getCarried();
            assertNull(ItemTools.component(scaled, "minecraft:map_post_processing"));
            assertNotEquals(baseId, org.domi.furnace.server.map.MapItems.mapId(scaled));
            assertEquals(2, org.domi.furnace.server.map.MapStore.of(scaled).scale());
            // 유리판: 잠금 — 같은 색을 가진 잠긴 새 번호
            org.domi.furnace.server.map.MapStore.get(baseId).setColor(1, 1, (byte) 33);
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(base);
            menu.getSlot(1).set(it("glass_pane", 1));
            menu.clicked(CartographyMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
            ItemStack locked = menu.getCarried();
            int lockedId = org.domi.furnace.server.map.MapItems.mapId(locked);
            assertNotEquals(baseId, lockedId);
            assertTrue(org.domi.furnace.server.map.MapStore.get(lockedId).locked());
            assertEquals(33, org.domi.furnace.server.map.MapStore.get(lockedId).color(1, 1));
            // 복제(빈 지도)는 같은 번호 두 장
            menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(0).set(base);
            menu.getSlot(1).set(it("map", 1));
            menu.clicked(CartographyMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
            assertEquals(2, menu.getCarried().count());
            assertEquals(baseId, org.domi.furnace.server.map.MapItems.mapId(menu.getCarried()));
        } finally {
            org.domi.furnace.server.map.MapStore.uninstall();
        }
    }
}
