// A09-02·03 재현: 마법 부여대 — (1) 창작 모드 플레이어는 청금석을 쓰지 않는다 (2) 시드 + 칸 번호는 int 덧셈(넘치면 음수)으로 난수 시드가 된다.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.EnchantTools;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.loot.Enchantments;
import org.domi.furnace.server.loot.LegacyNoiseRandom;
import org.domi.furnace.server.loot.LootRegistries;
import org.domi.furnace.server.loot.VanillaChestLootTest;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class A09EnchantReproTest {

    static final class P extends CraftingMenuTest.Player {
        int level = 40, seed = 12345;
        boolean creative;
        LootRegistries regs;
        P(PlayerInventory inv, org.domi.furnace.server.recipe.RecipeManager rm) { super(inv, rm); }
        @Override public boolean isCreative() { return creative; }
        @Override public int experienceLevel() { return level; }
        @Override public int enchantmentSeed() { return seed; }
        @Override public void onEnchantmentPerformed(int l) { if (!creative) level -= l; }
        @Override public LootRegistries lootRegistries() { return regs; }
        @Override public org.domi.furnace.server.plugin.GameEvents.EnchantApply enchantItem(AbstractContainerMenu menu, int[] table, ItemStack item, int button, int cost,
                                                                                          java.util.Map<String, Integer> enchants) {
            return new org.domi.furnace.server.plugin.GameEvents.EnchantApply(cost, enchants);
        }
    }

    P player;
    EnchantmentMenu menu;

    void open(int seed) throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        var inv = new PlayerInventory();
        player = new P(inv, TestRecipes.manager());
        player.seed = seed;
        player.regs = VanillaChestLootTest.vanillaLoot().registries();
        var invMenu = new InventoryMenu(inv, player);
        menu = new EnchantmentMenu(1, invMenu.inventoryContainer(), player, new int[]{0, 0, 0}, new BlockAccess() {
            @Override public boolean stillBlock() { return true; }
            @Override public long gameTime() { return 0; }
            @Override public void playSound(String s) { }
            @Override public int enchantingPower() { return 15; }
        });
    }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    /** 바닐라 EnchantmentMenu.clickMenuButton: 창작 모드(hasInfiniteMaterials)는 ItemStack.consume 이 줄이지 않는다 → 청금석 10개가 그대로. */
    @Test
    void creativePlayerKeepsLapis() throws Exception {
        open(12345);
        player.creative = true;
        menu.getSlot(0).set(it("diamond_sword", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 10));
        assertTrue(menu.costs[0] > 0);
        assertTrue(menu.clickMenuButton(player, 0));
        assertFalse(EnchantTools.levels(menu.getSlot(0).getItem(), EnchantTools.ENCHANTMENTS).isEmpty(), "마법이 붙었다");
        assertEquals(10, menu.getSlot(1).getItem().count(), "창작 모드는 청금석을 쓰지 않는다");
    }

    /** 대조: 서바이벌은 버튼 번호 + 1 개를 쓴다(이미 시험 있음). */
    @Test
    void survivalConsumesLapis() throws Exception {
        open(12345);
        menu.getSlot(0).set(it("diamond_sword", 1));
        menu.getSlot(1).set(it("lapis_lazuli", 10));
        assertTrue(menu.clickMenuButton(player, 1));
        assertEquals(8, menu.getSlot(1).getItem().count());
    }

    /**
     * 바닐라 getEnchantmentList: random.setSeed((long)(enchantmentSeed + slot)) — int 덧셈이라 Integer.MAX_VALUE + 1 은 Integer.MIN_VALUE.
     * Furnace 는 (long) seed + slot 이라 2147483648 이 된다. 여러 아이템·칸으로 돌려 고르는 목록이 다른 경우를 센다.
     */
    @Test
    void enchantListSeedWrapsLikeIntAddition() throws Exception {
        int mismatches = 0, checked = 0;
        for (String item : List.of("diamond_sword", "diamond_pickaxe", "bow", "diamond_chestplate", "iron_axe", "fishing_rod", "crossbow", "diamond_helmet")) {
            for (int slot = 1; slot <= 2; slot++) {
                open(Integer.MAX_VALUE);
                menu.getSlot(0).set(it(item, 1));
                menu.getSlot(1).set(it("lapis_lazuli", 10));
                int cost = menu.costs[slot];
                if (cost <= 0) continue;
                LegacyRandomSource r = new LegacyRandomSource(0);
                r.setSeed((long) (Integer.MAX_VALUE + slot));   // 바닐라: int 덧셈 뒤 long 으로
                List<Enchantments.TableEnchant> expected = Enchantments.selectForTable(new LegacyNoiseRandom(r), "minecraft:" + item, cost, player.regs);
                assertTrue(menu.clickMenuButton(player, slot));
                var got = new java.util.TreeMap<>(EnchantTools.levels(menu.getSlot(0).getItem(), EnchantTools.ENCHANTMENTS));
                var want = new java.util.TreeMap<String, Integer>();
                for (var e : expected) want.merge(e.id(), e.level(), Math::max);
                checked++;
                if (!want.equals(got)) mismatches++;
            }
        }
        assertTrue(checked > 0);
        assertEquals(0, mismatches, "시드가 넘칠 때 바닐라와 다른 목록 " + mismatches + "/" + checked);
    }
}
