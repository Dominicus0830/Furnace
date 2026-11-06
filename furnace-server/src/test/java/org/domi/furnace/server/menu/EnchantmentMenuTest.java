// 마법 부여대 시험: 비용 3칸(책장 15개면 셋째는 30 이상), 단서, 버튼으로 부여(레벨·청금석 소모·시드 갱신), 책 → 마법 부여된 책, 조건 부족.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.VanillaChestLootTest;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EnchantmentMenuTest {

    static final class P extends CraftingMenuTest.Player {
        int level = 40, seed = 12345;
        LootRegistries regs;
        boolean cancelPrepare, cancelApply;
        int prepared, applied;
        P(PlayerInventory inv, org.domi.furnace.server.recipe.RecipeManager rm) { super(inv, rm); }
        @Override public int experienceLevel() { return level; }
        @Override public int enchantmentSeed() { return seed; }
        @Override public void onEnchantmentPerformed(int l) { level -= l; seed = seed * 31 + 7; }
        @Override public LootRegistries lootRegistries() { return regs; }
        @Override public org.domi.furnace.server.plugin.GameEvents.EnchantOffer[] prepareEnchant(AbstractContainerMenu menu, int[] table, ItemStack item, int bookshelves,
                                                                                               org.domi.furnace.server.plugin.GameEvents.EnchantOffer[] offers) {
            prepared++;
            return cancelPrepare ? null : offers;
        }
        @Override public org.domi.furnace.server.plugin.GameEvents.EnchantApply enchantItem(AbstractContainerMenu menu, int[] table, ItemStack item, int button, int cost,
                                                                                          java.util.Map<String, Integer> enchants) {
            applied++;
            return cancelApply ? null : new org.domi.furnace.server.plugin.GameEvents.EnchantApply(cost, enchants);
        }
    }

    P player;
    EnchantmentMenu menu;
    int sounds;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        player = new P(inv, TestRecipes.manager());
        player.regs = VanillaChestLootTest.vanillaLoot().registries();
        var invMenu = new InventoryMenu(inv, player);
        menu = new EnchantmentMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { sounds++; }
            @Override public int enchantingPower() { return 15; }
        });
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    @Test
    void costsCluesAndEnchantingConsumesLevelsAndLapis() {
        menu.getSlot(0).set(it("diamond_sword", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 5));
        assertTrue(menu.costs[0] >= 1 && menu.costs[0] <= 10, "첫째: " + menu.costs[0]);
        assertTrue(menu.costs[2] >= 30, "책장 15개면 셋째는 30 이상: " + menu.costs[2]);
        assertTrue(menu.enchantClue[2] >= 0 && menu.levelClue[2] >= 1);
        assertTrue(menu.clickMenuButton(player, 2));
        var levels = EnchantTools.levels(menu.getSlot(0).getItem(), EnchantTools.ENCHANTMENTS);
        assertFalse(levels.isEmpty());
        assertEquals(37, player.level);
        assertEquals(2, menu.getSlot(1).getItem().count());
        assertEquals(1, sounds);
        assertNotEquals(12345, player.seed);
    }

    @Test
    void bookBecomesEnchantedBook() {
        menu.getSlot(0).set(it("book", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 3));
        assertTrue(menu.clickMenuButton(player, 0));
        ItemStack out = menu.getSlot(0).getItem();
        assertEquals("minecraft:enchanted_book", ItemTools.itemName(out));
        assertFalse(EnchantTools.levels(out, EnchantTools.STORED).isEmpty());
    }

    @Test
    void notEnoughLapisOrLevelsOrNonEnchantableRefuses() {
        menu.getSlot(0).set(it("diamond_sword", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 1));
        assertFalse(menu.clickMenuButton(player, 2), "청금석 3개 필요");
        menu.getSlot(1).set(it("lapis_lazuli", 3));
        player.level = 1;
        assertFalse(menu.clickMenuButton(player, 2), "레벨 부족");
        assertFalse(menu.clickMenuButton(player, 7), "범위 밖 번호");
        menu.getSlot(0).set(it("dirt", 1));
        assertEquals(0, menu.costs[0]);
    }

    @Test
    void quickMoveSendsOneItemToTargetSlotAndLapisToLapisSlot() {
        var inv = player.inv;
        inv.set(PlayerInventory.windowSlot(9), it("diamond_sword", 1));
        inv.set(PlayerInventory.windowSlot(10), it("lapis_lazuli", 4));
        int a = menu.findSlot(new InventoryMenu(inv, player).inventoryContainer(), 9);
        // 이 메뉴가 쓰는 플레이어 인벤토리 컨테이너로 칸을 찾는다
        Slot s2 = menu.getSlot(2);
        int idxSword = -1, idxLapis = -1;
        for (int i = 2; i < menu.slots.size(); i++) {
            Slot sl = menu.getSlot(i);
            if (sl.containerIndex() == 9) idxSword = i;
            if (sl.containerIndex() == 10) idxLapis = i;
        }
        menu.clicked(idxSword, 0, ClickType.QUICK_MOVE, player);
        assertEquals("minecraft:diamond_sword", ItemTools.itemName(menu.getSlot(0).getItem()));
        menu.clicked(idxLapis, 0, ClickType.QUICK_MOVE, player);
        assertEquals(4, menu.getSlot(1).getItem().count());
    }

    /** 이벤트 취소 계약: PrepareItemEnchantEvent 를 취소하면 제안이 하나도 없다(재료는 그대로). */
    @Test
    void prepareEnchantCancelOffersNothing() {
        player.cancelPrepare = true;
        menu.getSlot(0).set(it("diamond_sword", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 5));
        assertTrue(player.prepared >= 1, "이벤트는 불렸다");
        for (int i = 0; i < 3; i++) assertEquals(0, menu.costs[i], "취소했으면 " + i + "번 칸에 제안이 없다");
        assertEquals("minecraft:diamond_sword", ItemTools.itemName(menu.getSlot(0).getItem()));
        assertEquals(5, menu.getSlot(1).getItem().count());
    }

    /** 이벤트 취소 계약: EnchantItemEvent 를 취소하면 아이템·청금석·레벨·시드가 그대로다. */
    @Test
    void enchantItemCancelKeepsItemLapisLevelsAndSeed() {
        menu.getSlot(0).set(it("diamond_sword", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 5));
        var swordBefore = menu.getSlot(0).getItem();
        player.cancelApply = true;
        assertTrue(menu.clickMenuButton(player, 2), "버튼 처리는 끝난다");
        assertEquals(1, player.applied);
        assertSame(swordBefore, menu.getSlot(0).getItem());
        assertTrue(EnchantTools.levels(menu.getSlot(0).getItem(), EnchantTools.ENCHANTMENTS).isEmpty(), "마법이 붙지 않았다");
        assertEquals(5, menu.getSlot(1).getItem().count());
        assertEquals(40, player.level);
        assertEquals(12345, player.seed);
        assertEquals(0, sounds);
        player.cancelApply = false;   // 대조: 허용하면 부여된다
        assertTrue(menu.clickMenuButton(player, 2));
        assertFalse(EnchantTools.levels(menu.getSlot(0).getItem(), EnchantTools.ENCHANTMENTS).isEmpty());
    }
}
