// 대장장이 작업대 시험: 네더라이트 업그레이드(밑감 컴포넌트 유지), 갑옷 장식, 칸별 허용 아이템, 꺼내기 때 재료 1개씩 소모.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmithingMenuTest {

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    SmithingMenu menu;
    int events;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        menu = new SmithingMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { }
            @Override public void levelEvent(int t) { if (t == 1044) events++; }
        });
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    @Test
    void netheriteUpgradeKeepsBaseComponentsAndConsumesOneOfEach() {
        ItemStack sword = ItemTools.withDamage(it("diamond_sword", 1), 100);
        menu.getSlot(0).set(it("netherite_upgrade_smithing_template", 2));
        menu.getSlot(1).set(sword);
        menu.getSlot(2).set(it("netherite_ingot", 1));
        ItemStack out = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        assertEquals("minecraft:netherite_sword", ItemTools.itemName(out));
        assertEquals(100, ItemTools.damage(out));
        menu.clicked(SmithingMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals("minecraft:netherite_sword", ItemTools.itemName(menu.getCarried()));
        assertEquals(1, menu.getSlot(0).getItem().count());
        assertTrue(menu.getSlot(1).getItem().isEmpty());
        assertTrue(menu.getSlot(2).getItem().isEmpty());
        assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty());
        assertEquals(1, events);
        assertEquals(1, player.awarded.size());
    }

    @Test
    void armorTrimAddsTrimComponentAndRejectsSameTrim() {
        menu.getSlot(0).set(it("coast_armor_trim_smithing_template", 1));
        menu.getSlot(1).set(it("iron_chestplate", 1));
        menu.getSlot(2).set(it("amethyst_shard", 1));
        ItemStack out = menu.getSlot(SmithingMenu.RESULT_SLOT).getItem();
        assertEquals("minecraft:iron_chestplate", ItemTools.itemName(out));
        Object trim = ItemTools.component(out, "minecraft:trim");
        assertEquals(Map.of("material", "minecraft:amethyst", "pattern", "minecraft:coast"), trim);
        // 같은 장식이 이미 있으면 결과 없음
        menu.getSlot(1).set(out);
        assertTrue(menu.getSlot(SmithingMenu.RESULT_SLOT).getItem().isEmpty());
    }

    @Test
    void slotsAcceptOnlyRecipeIngredients() {
        assertTrue(menu.getSlot(0).mayPlace(it("netherite_upgrade_smithing_template", 1)));
        assertFalse(menu.getSlot(0).mayPlace(it("dirt", 1)));
        assertTrue(menu.getSlot(1).mayPlace(it("diamond_sword", 1)));
        assertTrue(menu.getSlot(2).mayPlace(it("netherite_ingot", 1)));
        assertFalse(menu.getSlot(SmithingMenu.RESULT_SLOT).mayPlace(it("netherite_sword", 1)));
    }

    @Test
    void resultCannotBeTakenWithoutRecipe() {
        assertFalse(menu.getSlot(SmithingMenu.RESULT_SLOT).mayPickup(player));
    }
}
