// 모루 시험: 이름 바꾸기, 재료로 수리, 인챈트 책 합치기(비용·수리 비용), 레벨 부족, 40레벨 이상, 만들기 모드, 꺼내기 정산.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.Enchantment;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AnvilMenuTest {

    static final class P extends CraftingMenuTest.Player {
        int level = 5;
        boolean creative;
        P(PlayerInventory inv, org.domi.furnace.server.recipe.RecipeManager rm) { super(inv, rm); }
        @Override public boolean isCreative() { return creative; }
        @Override public int experienceLevel() { return level; }
        @Override public void giveExperienceLevels(int l) { level += l; }
        @Override public Enchantment enchantment(String id) {
            return switch (id) {
                case "minecraft:sharpness" -> new Enchantment(id, 10, 5, 1, 11, 21, 11, null, null, null, List.of("mainhand"), 1);
                case "minecraft:unbreaking" -> new Enchantment(id, 5, 3, 5, 8, 55, 8, null, null, null, List.of("any"), 2);
                default -> null;
            };
        }
    }

    P player;
    AnvilMenu menu;
    int used;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        player = new P(inv, TestRecipes.manager());
        var invMenu = new InventoryMenu(inv, player);
        menu = new AnvilMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { }
            @Override public void anvilUsed(boolean inf) { used++; }
        });
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    ItemStack result() { return menu.getSlot(AnvilMenu.RESULT_SLOT).getItem(); }

    @Test
    void renameCostsOneLevelAndSetsCustomName() {
        menu.getSlot(0).set(it("iron_sword", 1));
        menu.setItemName("Foo");
        assertEquals("\"Foo\"", ItemTools.component(result(), "minecraft:custom_name"));
        assertEquals(1, menu.cost());
        assertEquals(0, ItemTools.component(result(), "minecraft:repair_cost") instanceof Number n ? n.intValue() - 0 : -1, "이름만 바꾸면 수리 비용은 오르지 않는다");
        menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals("minecraft:iron_sword", ItemTools.itemName(menu.getCarried()));
        assertEquals(4, player.level);
        assertTrue(menu.getSlot(0).getItem().isEmpty());
        assertEquals(1, used);
    }

    @Test
    void renamingToTheDefaultNameCostsNothing() {   // 이름 없는 아이템의 기본 이름(en_us)과 같으면 바뀐 게 없다 — AnvilMenu 의 getHoverName().getString() 비교
        menu.getSlot(0).set(it("iron_sword", 1));
        menu.setItemName("Iron Sword");
        assertTrue(result().isEmpty());
    }

    @Test
    void renamingABlockItemToItsDefaultNameCostsNothing() {
        menu.getSlot(0).set(it("stone", 1));
        menu.setItemName("Stone");
        assertTrue(result().isEmpty());
        menu.setItemName("Stone2");
        assertEquals("\"Stone2\"", ItemTools.component(result(), "minecraft:custom_name"));
    }

    @Test
    void repairWithIngotRestoresQuarterPerItem() {
        menu.getSlot(0).set(ItemTools.withDamage(it("iron_sword", 1), 100));
        menu.getSlot(1).set(it("iron_ingot", 3));
        assertEquals(0, ItemTools.damage(result()), "62 + 38 로 두 번에 다 고친다");
        assertEquals(2, menu.cost());
        assertEquals(1, ItemTools.component(result(), "minecraft:repair_cost"));
        menu.clicked(AnvilMenu.RESULT_SLOT, 0, ClickType.PICKUP, player);
        assertEquals(1, menu.getSlot(1).getItem().count(), "철괴는 쓴 만큼(2개)만 줄어든다");
    }

    @Test
    void enchantedBookMergesWithBookDiscountAndRaisesRepairCost() {
        menu.getSlot(0).set(it("iron_sword", 1));
        menu.getSlot(1).set(EnchantTools.update(it("enchanted_book", 1), m -> m.put("minecraft:sharpness", 2)));
        assertEquals(Map.of("minecraft:sharpness", 2), EnchantTools.levels(result(), EnchantTools.ENCHANTMENTS));
        assertEquals(2, menu.cost(), "max(1, 1/2) * 레벨 2");
        assertEquals(1, ItemTools.component(result(), "minecraft:repair_cost"));
    }

    @Test
    void notEnoughLevelsCannotTake() {
        player.level = 0;
        menu.getSlot(0).set(it("iron_sword", 1));
        menu.setItemName("Foo");
        assertFalse(menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player));
        player.creative = true;
        assertTrue(menu.getSlot(AnvilMenu.RESULT_SLOT).mayPickup(player));
    }

    @Test
    void tooExpensiveGivesNoResultUnlessCreative() {
        menu.getSlot(0).set(EnchantTools.update(it("iron_sword", 1), m -> m.put("minecraft:unbreaking", 3)));
        menu.getSlot(1).set(EnchantTools.update(it("enchanted_book", 1), m -> m.put("minecraft:unbreaking", 3)));
        // 3 + 3 → 레벨 3(상한), 비용 max(1, 2/2)*3 = 3 → 싸다. 비싸게: 큰 수리 비용
        ItemStack worn = ItemTools.with(menu.getSlot(0).getItem(), "minecraft:repair_cost", 63);
        menu.getSlot(0).set(worn);
        assertTrue(result().isEmpty());
        player.creative = true;
        menu.getSlot(0).set(worn);
        assertFalse(result().isEmpty());
    }

    @Test
    void differentUndamagedItemsGiveNothing() {
        menu.getSlot(0).set(it("iron_sword", 1));
        menu.getSlot(1).set(it("dirt", 1));
        assertTrue(result().isEmpty());
        assertEquals(0, menu.cost());
    }

    @Test
    void longNamesAreIgnoredAndColorCodesStripped() {
        menu.getSlot(0).set(it("iron_sword", 1));
        assertFalse(menu.setItemName("x".repeat(51)));
        assertTrue(menu.setItemName("§aBar"));
        assertEquals("\"aBar\"", ItemTools.component(result(), "minecraft:custom_name"));
    }
}
