// 숫돌 시험: 인챈트 지우기(저주 유지), 같은 아이템 합치기(내구도 보너스), 책 → 책, 결과 꺼내기 때 재료 소모·경험치 난수·이벤트.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GrindstoneMenuTest {

    PlayerInventory inv;
    CraftingMenuTest.Player player;
    GrindstoneMenu menu;
    int events, xp;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new CraftingMenuTest.Player(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        menu = new GrindstoneMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { }
            @Override public void levelEvent(int t) { if (t == 1042) events++; }
            @Override public void awardExperience(int a) { xp += a; }
        });
    }

    static ItemStack ench(ItemStack s, String... pairs) {
        return EnchantTools.update(s, m -> { for (int i = 0; i < pairs.length; i += 2) m.put(pairs[i], Integer.parseInt(pairs[i + 1])); });
    }

    @Test
    void singleItemLosesNonCurseEnchantsAndRepairCostResets() {
        ItemStack sword = ench(TestRecipes.item("diamond_sword", 1), "minecraft:sharpness", "3", "minecraft:vanishing_curse", "1");
        menu.getSlot(0).set(sword);
        ItemStack out = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        assertFalse(out.isEmpty());
        assertEquals(Map.of("minecraft:vanishing_curse", 1), EnchantTools.levels(out, EnchantTools.ENCHANTMENTS));
        assertEquals(1, ItemTools.component(out, "minecraft:repair_cost"));
    }

    @Test
    void plainUnenchantedItemGivesNothing() {
        menu.getSlot(0).set(TestRecipes.item("diamond_sword", 1));
        assertTrue(menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty());
    }

    @Test
    void mergingTwoDamagedToolsAddsDurabilityBonus() {
        ItemStack a = ItemTools.withDamage(TestRecipes.item("diamond_pickaxe", 1), 1000);
        ItemStack b = ItemTools.withDamage(TestRecipes.item("diamond_pickaxe", 1), 1200);
        menu.getSlot(0).set(a);
        menu.getSlot(1).set(b);
        ItemStack out = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        int max = ItemTools.maxDamage(a);
        int extra = (max - 1000) + (max - 1200) + max * 5 / 100;
        assertEquals(Math.max(max - extra, 0), ItemTools.damage(out));
    }

    @Test
    void differentItemsGiveNothing() {
        menu.getSlot(0).set(ench(TestRecipes.item("diamond_sword", 1), "minecraft:sharpness", "1"));
        menu.getSlot(1).set(ench(TestRecipes.item("diamond_axe", 1), "minecraft:sharpness", "1"));
        assertTrue(menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty());
    }

    @Test
    void enchantedBookBecomesBookAndTakingConsumesInputs() {
        menu.getSlot(0).set(ench(TestRecipes.item("enchanted_book", 1), "minecraft:sharpness", "2"));
        ItemStack out = menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem();
        assertEquals("minecraft:book", ItemTools.itemName(out));
        menu.clicked(GrindstoneMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals("minecraft:book", ItemTools.itemName(menu.getCarried()));
        assertTrue(menu.getSlot(0).getItem().isEmpty());
        assertTrue(menu.getSlot(GrindstoneMenu.RESULT_SLOT).getItem().isEmpty());
        assertEquals(1, events);
    }

    @Test
    void inputSlotsRejectPlainItems() {
        assertFalse(menu.getSlot(0).mayPlace(TestRecipes.item("dirt", 1)));
        assertTrue(menu.getSlot(0).mayPlace(TestRecipes.item("diamond_sword", 1)));
        assertTrue(menu.getSlot(0).mayPlace(ench(TestRecipes.item("enchanted_book", 1), "minecraft:sharpness", "1")));
    }
}
