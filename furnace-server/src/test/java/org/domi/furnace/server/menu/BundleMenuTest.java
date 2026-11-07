// M18: 가방(bundle) 클릭 — BundleItem.overrideStackedOnOther / overrideOtherStackedOnMe, BundleContents 무게(분수)·쌓기·꺼내기. 기능 플래그가 켜져 있어야 한다.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.domi.furnace.server.data.FeatureFlags;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BundleMenuTest {

    static final class P extends CraftingMenuTest.Player {
        final List<String> sounds = new ArrayList<>();
        P(PlayerInventory inv) throws Exception { super(inv, TestRecipes.manager()); }
        @Override public void playBundleSound(String sound) { sounds.add(sound); }
    }

    P player;
    InventoryMenu menu;

    @BeforeEach
    void setUp() throws Exception {
        assumeTrue(TestRecipes.manager() != null, "로컬 바닐라 데이터 없음");
        FeatureFlags.install(Set.of("minecraft:vanilla", "minecraft:bundle"));
        var inv = new PlayerInventory();
        player = new P(inv);
        menu = new InventoryMenu(inv, player);
    }

    @AfterEach
    void tearDown() { FeatureFlags.install(Set.of("minecraft:vanilla")); }

    static ItemStack it(String n, int c) { return TestRecipes.item(n, c); }

    static final int A = 36, B = 37;

    private void rightClick(int slot) { menu.clicked(slot, 1, ClickType.PICKUP, player); }

    private List<ItemStack> contents(ItemStack bundle) { return BundleContents.of(bundle).items(); }

    @Test
    void rightClickingABundleSlotWithItemsCarriedInsertsThem() {
        menu.getSlot(A).set(it("bundle", 1));
        menu.setCarried(it("stone", 10));
        rightClick(A);
        assertTrue(menu.getCarried().isEmpty());
        var c = contents(menu.getSlot(A).getItem());
        assertEquals(1, c.size());
        assertEquals(10, c.get(0).count());
        assertEquals(List.of("minecraft:item.bundle.insert"), player.sounds);
    }

    @Test
    void rightClickingABundleSlotWithEmptyCursorTakesOneStackOut() {
        menu.getSlot(A).set(it("bundle", 1));
        menu.setCarried(it("stone", 10));
        rightClick(A);
        menu.setCarried(it("dirt", 5));
        rightClick(A);
        menu.setCarried(ItemStack.EMPTY);
        rightClick(A);   // 가장 최근 것(흙)이 앞이라 먼저 나온다
        assertEquals("minecraft:dirt", org.domi.furnace.server.item.ItemTools.itemName(menu.getCarried()));
        assertEquals(5, menu.getCarried().count());
        assertEquals(1, contents(menu.getSlot(A).getItem()).size());
        assertEquals("minecraft:item.bundle.remove_one", player.sounds.get(player.sounds.size() - 1));
    }

    @Test
    void carriedBundleRightClickingAnItemSlotPullsItIn() {
        menu.setCarried(it("bundle", 1));
        menu.getSlot(B).set(it("stone", 30));
        rightClick(B);
        assertTrue(menu.getSlot(B).getItem().isEmpty());
        assertEquals(30, contents(menu.getCarried()).get(0).count());
        assertEquals(List.of("minecraft:item.bundle.insert"), player.sounds);
    }

    @Test
    void carriedBundleRightClickingAnEmptySlotPutsOneStackDown() {
        ItemStack bundle = it("bundle", 1);
        var c = BundleContents.of(bundle);
        c.tryInsert(it("stone", 20));
        menu.setCarried(c.applyTo(bundle));
        rightClick(B);
        assertEquals(20, menu.getSlot(B).getItem().count());
        assertTrue(BundleContents.of(menu.getCarried()).isEmpty());
        assertEquals(List.of("minecraft:item.bundle.remove_one"), player.sounds);
    }

    @Test
    void leftClickIsAnOrdinarySwap() {
        menu.getSlot(A).set(it("bundle", 1));
        menu.setCarried(it("stone", 10));
        menu.clicked(A, 0, ClickType.PICKUP, player);
        assertTrue(player.sounds.isEmpty());
        assertEquals("minecraft:bundle", org.domi.furnace.server.item.ItemTools.itemName(menu.getCarried()), "그냥 바뀐다");
    }

    @Test
    void featureOffMeansOrdinaryClicks() {
        FeatureFlags.install(Set.of("minecraft:vanilla"));
        menu.getSlot(A).set(it("bundle", 1));
        menu.setCarried(it("stone", 10));
        rightClick(A);
        assertTrue(player.sounds.isEmpty());
        assertEquals("minecraft:bundle", org.domi.furnace.server.item.ItemTools.itemName(menu.getCarried()), "그냥 서로 바뀐다");
        assertEquals(10, menu.getSlot(A).getItem().count());
    }

    @Test
    void weightLimitsInsertionAndStacksMerge() {
        var c = BundleContents.of(it("bundle", 1));
        assertEquals(64, c.tryInsert(it("stone", 64)), "돌 64개 = 무게 1");
        assertEquals(0, c.tryInsert(it("stone", 1)));
        var d = BundleContents.of(it("bundle", 1));
        assertEquals(16, d.tryInsert(it("ender_pearl", 16)), "엔더 진주는 16개가 무게 1");
        var e = BundleContents.of(it("bundle", 1));
        assertEquals(32, e.tryInsert(it("stone", 32)));
        assertEquals(8, e.tryInsert(it("ender_pearl", 8)), "남은 무게 1/2 = 진주 8개");
        assertEquals(0, e.tryInsert(it("stone", 1)));
        assertEquals(2, e.items().size());
        // 같은 것은 합쳐져 맨 앞으로
        var f = BundleContents.of(it("bundle", 1));
        f.tryInsert(it("stone", 5));
        f.tryInsert(it("dirt", 3));
        f.tryInsert(it("stone", 4));
        assertEquals(2, f.items().size());
        assertEquals(9, f.items().get(0).count());
        assertEquals("minecraft:stone", org.domi.furnace.server.item.ItemTools.itemName(f.items().get(0)));
    }

    @Test
    void shulkerBoxesAndNothingCannotBeInserted() {
        var c = BundleContents.of(it("bundle", 1));
        assertEquals(0, c.tryInsert(it("shulker_box", 1)));
        assertEquals(0, c.tryInsert(ItemStack.EMPTY));
        assertTrue(c.isEmpty());
    }

    @Test
    void nestedBundleWeighsOneSixteenthPlusItsContents() {
        ItemStack inner = it("bundle", 1);
        var ic = BundleContents.of(inner);
        ic.tryInsert(it("stone", 32));   // 무게 1/2
        inner = ic.applyTo(inner);
        var outer = BundleContents.of(it("bundle", 1));
        assertEquals(1, outer.tryInsert(inner));
        long[] w = outer.weight();
        assertEquals(9, w[0] * 16 / w[1] , "1/16 + 1/2 = 9/16");
        // 남은 무게 7/16 → 진주 7개
        assertEquals(7, outer.tryInsert(it("ender_pearl", 16)));
    }
}
