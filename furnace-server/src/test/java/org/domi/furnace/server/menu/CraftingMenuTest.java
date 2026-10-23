// 제작 격자 시험(인벤토리 창 2×2 · 제작대 3×3): 실제 바닐라 레시피로 결과 칸, 꺼내기(클릭·shift-클릭), 남는 아이템, 레시피 해금.
package org.domi.furnace.server.menu;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.recipe.RecipeManager;
import org.domi.furnace.server.recipe.TestRecipes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CraftingMenuTest {

    static final class Player implements MenuPlayer {
        final PlayerInventory inv;
        final RecipeManager recipes;
        final List<ItemStack> dropped = new ArrayList<>();
        final List<ResourceId> awarded = new ArrayList<>();
        final List<ResourceId> crafted = new ArrayList<>();
        final java.util.Set<ResourceId> known = new java.util.HashSet<>();
        boolean limited;

        Player(PlayerInventory inv, RecipeManager recipes) { this.inv = inv; this.recipes = recipes; }

        @Override public boolean isCreative() { return false; }
        @Override public boolean isSpectator() { return false; }
        @Override public boolean isDeadOrDying() { return false; }
        @Override public boolean hasDisconnected() { return false; }
        @Override public void drop(ItemStack s, boolean t) { if (!s.isEmpty()) dropped.add(s); }
        @Override public void placeItemBackInInventory(ItemStack s) {
            ItemStack left = inv.add(s, false, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(s), false);
            if (!left.isEmpty()) drop(left, false);
        }
        @Override public ItemStack addToInventory(ItemStack s) { return inv.add(s, false, org.domi.furnace.server.item.ItemStackCodecs.maxStackSize(s), false); }
        @Override public ItemStack inventoryItem(int i) { return inv.get(PlayerInventory.windowSlot(i)); }
        @Override public void setInventoryItem(int i, ItemStack s) { inv.set(PlayerInventory.windowSlot(i), s); }
        @Override public EquipSlot equipmentSlotFor(ItemStack s) { return EquipSlot.MAINHAND; }
        @Override public boolean hasBindingCurse(ItemStack s) { return false; }
        @Override public double distanceToSqr(double x, double y, double z) { return 0; }
        @Override public RecipeManager recipes() { return recipes; }
        @Override public boolean doLimitedCrafting() { return limited; }
        @Override public boolean hasRecipe(ResourceId id) { return known.contains(id); }
        @Override public void awardRecipes(List<ResourceId> ids) { awarded.addAll(ids); known.addAll(ids); }
        @Override public void triggerRecipeCrafted(ResourceId id, List<ItemStack> grid) { crafted.add(id); }
    }

    static final class Recorder implements MenuSynchronizer {
        final List<String> events = new ArrayList<>();
        @Override public void sendInitialData(AbstractContainerMenu m, List<ItemStack> items, ItemStack carried, int[] data) { events.add("init#" + m.incrementStateId()); }
        @Override public void sendSlotChange(AbstractContainerMenu m, int slot, ItemStack s) { events.add("slot" + slot + "=" + s.count() + "#" + m.incrementStateId()); }
        @Override public void sendCarriedChange(AbstractContainerMenu m, ItemStack s) { events.add("carried=" + s.count() + "#" + m.incrementStateId()); }
        @Override public void sendDataChange(AbstractContainerMenu m, int id, int v) { }
    }

    PlayerInventory inv;
    Player player;
    InventoryMenu menu;

    @BeforeEach
    void setUp() throws Exception {
        RecipeManager rm = TestRecipes.manager();
        assumeTrue(rm != null, "로컬 바닐라 데이터 없음");
        inv = new PlayerInventory();
        player = new Player(inv, rm);
        menu = new InventoryMenu(inv, player);
    }

    static ItemStack it(String name, int n) { return TestRecipes.item(name, n); }

    void click(AbstractContainerMenu m, int slot, int button, ClickType type) { m.clicked(slot, button, type, player); }

    String name(ItemStack s) { return s.isEmpty() ? "empty" : org.domi.furnace.server.item.ItemContext.name("minecraft:item", s.itemId()) + "x" + s.count(); }

    @Test
    void placingLogInGridShowsPlanksInResultSlot() {
        inv.set(36, it("oak_log", 3));
        click(menu, 36, 0, ClickType.PICKUP);   // 커서에 통나무 3
        click(menu, 1, 1, ClickType.PICKUP);    // 격자 첫 칸에 하나
        assertEquals("minecraft:oak_planksx4", name(menu.getSlot(0).getItem()));
    }

    @Test
    void takingResultConsumesOneIngredientAndUnlocksRecipe() {
        inv.set(36, it("oak_log", 3));
        click(menu, 36, 0, ClickType.PICKUP);
        click(menu, 1, 1, ClickType.PICKUP);
        click(menu, 0, 0, ClickType.PICKUP);    // 결과 꺼내기 — 커서에 통나무 2가 있으므로 결과(판자)는 못 든다
        assertTrue(menu.getSlot(0).hasItem(), "커서가 다른 아이템을 들고 있으면 결과를 못 집는다");
        click(menu, -999, 0, ClickType.PICKUP); // 남은 통나무는 버린다
        click(menu, 0, 0, ClickType.PICKUP);
        assertEquals("minecraft:oak_planksx4", name(menu.getCarried()));
        assertTrue(menu.getSlot(1).getItem().isEmpty(), "재료 통나무 하나가 줄어 격자가 비었다");
        assertTrue(menu.getSlot(0).getItem().isEmpty(), "결과 칸도 다시 비었다");
        assertEquals(List.of(ResourceId.parse("minecraft:oak_planks")), player.awarded);
        assertEquals(List.of(ResourceId.parse("minecraft:oak_planks")), player.crafted);
    }

    @Test
    void shiftClickResultCraftsRepeatedlyIntoInventory() {
        inv.set(36, it("oak_log", 3));
        click(menu, 36, 0, ClickType.PICKUP);
        click(menu, 1, 0, ClickType.PICKUP);    // 통나무 3 모두 격자 첫 칸으로
        assertEquals("minecraft:oak_planksx4", name(menu.getSlot(0).getItem()));
        click(menu, 0, 0, ClickType.QUICK_MOVE);
        assertTrue(menu.getSlot(1).getItem().isEmpty(), "통나무 세 개를 다 썼다");
        int planks = 0;
        for (ItemStack s : inv.snapshot()) if (!s.isEmpty() && s.itemId() == it("oak_planks", 1).itemId()) planks += s.count();
        assertEquals(12, planks);
    }

    @Test
    void gridChangeSendsResultSlotWithIncrementedStateId() {
        Recorder rec = new Recorder();
        menu.setSynchronizer(rec);
        rec.events.clear();
        inv.set(36, it("oak_log", 1));
        click(menu, 36, 0, ClickType.PICKUP);
        rec.events.clear();
        click(menu, 1, 0, ClickType.PICKUP);
        assertTrue(rec.events.get(0).startsWith("slot0=4#"), "격자가 바뀌면 결과 칸을 바로 보낸다: " + rec.events);
    }

    @Test
    void limitedCraftingHidesUnknownRecipes() {
        player.limited = true;
        inv.set(36, it("oak_log", 1));
        click(menu, 36, 0, ClickType.PICKUP);
        click(menu, 1, 0, ClickType.PICKUP);
        assertTrue(menu.getSlot(0).getItem().isEmpty(), "아직 모르는 레시피는 결과가 없다");
        player.known.add(ResourceId.parse("minecraft:oak_planks"));
        click(menu, 1, 0, ClickType.PICKUP);   // 집었다가
        click(menu, 1, 0, ClickType.PICKUP);   // 다시 놓으면 재계산
        assertEquals("minecraft:oak_planksx4", name(menu.getSlot(0).getItem()));
    }

    @Test
    void closingReturnsGridAndCursorToInventory() {
        inv.set(36, it("oak_log", 2));
        click(menu, 36, 0, ClickType.PICKUP);
        click(menu, 1, 1, ClickType.PICKUP);
        menu.removed(player);
        assertTrue(inv.get(1).isEmpty());
        assertTrue(menu.getCarried().isEmpty());
        int logs = 0;
        for (ItemStack s : inv.snapshot()) if (!s.isEmpty()) logs += s.count();
        assertEquals(2, logs);
    }

    // ------------------------------------------------------------------ 제작대 3×3

    CraftingMenu table() {
        return new CraftingMenu(1, new PlayerInventoryContainer(inv), player, new int[]{0, 0, 0}, () -> true);
    }

    @Test
    void tableCraftsCakeAndLeavesBuckets() {
        CraftingMenu t = table();
        String[] grid = {"milk_bucket", "milk_bucket", "milk_bucket", "sugar", "egg", "sugar", "wheat", "wheat", "wheat"};
        for (int i = 0; i < 9; i++) t.getSlot(1 + i).setByPlayer(it(grid[i], 1));
        assertEquals("minecraft:cakex1", name(t.getSlot(0).getItem()));
        click(t, 0, 0, ClickType.PICKUP);
        assertEquals("minecraft:cakex1", name(t.getCarried()));
        for (int i = 0; i < 3; i++) assertEquals("minecraft:bucketx1", name(t.getSlot(1 + i).getItem()), "우유 양동이 자리에 빈 양동이");
        for (int i = 3; i < 9; i++) assertTrue(t.getSlot(1 + i).getItem().isEmpty());
    }

    @Test
    void tableQuickMoveFromInventoryGoesToGridFirst() {
        CraftingMenu t = table();
        inv.set(9, it("oak_planks", 4));       // 주 인벤토리 첫 칸 = 메뉴 10
        click(t, 10, 0, ClickType.QUICK_MOVE);
        assertEquals(4, t.getSlot(1).getItem().count() + t.getSlot(2).getItem().count() + t.getSlot(3).getItem().count()
                + t.getSlot(4).getItem().count() + t.getSlot(5).getItem().count() + t.getSlot(6).getItem().count()
                + t.getSlot(7).getItem().count() + t.getSlot(8).getItem().count() + t.getSlot(9).getItem().count());
    }

    @Test
    void tableRemovedReturnsGrid() {
        CraftingMenu t = table();
        t.getSlot(5).setByPlayer(it("stick", 3));
        t.removed(player);
        int sticks = 0;
        for (ItemStack s : inv.snapshot()) if (!s.isEmpty()) sticks += s.count();
        assertEquals(3, sticks);
    }

    @Test
    void tableStillValidNeedsBlockAndRange() {
        boolean[] block = {true};
        CraftingMenu t = new CraftingMenu(1, new PlayerInventoryContainer(inv), player, new int[]{0, 0, 0}, () -> block[0]);
        assertTrue(t.stillValid(player));
        block[0] = false;
        assertFalse(t.stillValid(player));
    }
}
