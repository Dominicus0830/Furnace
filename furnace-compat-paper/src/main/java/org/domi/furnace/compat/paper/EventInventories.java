// 이벤트가 주는 창 종류별 위쪽 인벤토리(Bukkit CraftingInventory·AnvilInventory·BrewerInventory·EnchantingInventory)와 레시피.
// 모두 이벤트 순간의 사본(SnapshotInventory) 위에 있고, 이벤트가 되돌려 받는 값(제작 결과 칸, 모루 비용·이름)만 바꿀 수 있다.
package org.domi.furnace.compat.paper;

import java.util.List;
import java.util.function.IntPredicate;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

final class EventInventories {

    private EventInventories() {}

    /** Furnace 레시피 id 와 결과. Bukkit 레시피 모양(ShapedRecipe 등)은 Furnace 가 알리지 않아 키와 결과만 안다. */
    record CompatRecipe(NamespacedKey key, org.domi.furnace.api.item.ItemStack result) implements org.bukkit.inventory.Recipe, Keyed {
        /** id 가 null 이면 null. */
        static CompatRecipe of(String id, org.domi.furnace.api.item.ItemStack result) {
            if (id == null || id.isEmpty()) return null;
            NamespacedKey k = NamespacedKey.fromString(id);
            return k == null ? null : new CompatRecipe(k, result == null ? org.domi.furnace.api.item.ItemStack.EMPTY : result);
        }

        @Override
        public ItemStack getResult() { return CompatItems.copyOf(result); }

        @Override
        public NamespacedKey getKey() { return key; }
    }

    /** 작업대(3×3)·플레이어 2×2: 칸 0 결과, 1.. 격자. */
    static final class Crafting extends SnapshotInventory implements org.bukkit.inventory.CraftingInventory {
        private final CompatRecipe recipe;

        Crafting(InventoryType type, List<org.domi.furnace.api.item.ItemStack> contents, int size, String key, IntPredicate writable, CompatRecipe recipe) {
            super(type, contents, size, key, writable, null);
            this.recipe = recipe;
        }

        @Override
        public ItemStack getResult() { return bukkitAt(0); }

        @Override
        public ItemStack[] getMatrix() {
            ItemStack[] m = new ItemStack[getSize() - 1];
            for (int i = 0; i < m.length; i++) m[i] = bukkitAt(i + 1);
            return m;
        }

        @Override
        public void setResult(ItemStack newResult) { setItem(0, newResult); }

        @Override
        public void setMatrix(ItemStack[] contents) {
            if (contents.length > getSize() - 1) throw new IllegalArgumentException("Invalid inventory size (" + contents.length + "); expected " + (getSize() - 1) + " or less");
            for (int i = 0; i < getSize() - 1; i++) setItem(i + 1, i < contents.length ? contents[i] : null);
        }

        @Override
        public org.bukkit.inventory.Recipe getRecipe() { return recipe; }
    }

    /** 모루 결과를 정하는 순간의 값(PrepareAnvilEvent) — 다른 이벤트의 모루 창은 이 값을 모른다(null). */
    static final class AnvilState {
        /** 바닐라 모루의 최대 비용(이 값 이상이면 생존 모드에서 "너무 비쌈"). */
        static final int MAX_REPAIR_COST = 40;

        final String renameText;
        int repairCost;

        AnvilState(String renameText, int repairCost) {
            this.renameText = renameText;
            this.repairCost = repairCost;
        }
    }

    static UnsupportedFeature anvilUnknown(String what) {
        return new UnsupportedFeature(what, "모루 비용·이름은 Furnace 가 모루 결과를 정할 때(PrepareAnvilEvent)만 알린다");
    }

    static UnsupportedFeature anvilFixed(String what) {
        return new UnsupportedFeature(what, "Furnace 모루 이벤트는 재료 소모 개수·최대 비용을 바꾸지 못한다");
    }

    /** 모루: 0 첫째, 1 둘째, 2 결과. */
    @SuppressWarnings("removal")   // 옛 이름(AnvilInventory 비용 메서드)도 Paper 처럼 받는다
    static final class Anvil extends SnapshotInventory implements org.bukkit.inventory.AnvilInventory {
        private final AnvilState state;

        Anvil(List<org.domi.furnace.api.item.ItemStack> contents, int size, String key, AnvilState state) {
            super(InventoryType.ANVIL, contents, size, key);
            this.state = state;
        }

        private AnvilState state(String what) {
            if (state == null) throw anvilUnknown(what);
            return state;
        }

        @Override
        public String getRenameText() { return state("AnvilInventory#getRenameText").renameText; }

        @Override
        public int getRepairCostAmount() { throw anvilUnknown("AnvilInventory#getRepairCostAmount"); }

        @Override
        public void setRepairCostAmount(int amount) { throw anvilFixed("AnvilInventory#setRepairCostAmount"); }

        @Override
        public int getRepairCost() { return state("AnvilInventory#getRepairCost").repairCost; }

        @Override
        public void setRepairCost(int levels) { state("AnvilInventory#setRepairCost").repairCost = levels; }

        @Override
        public int getMaximumRepairCost() { return AnvilState.MAX_REPAIR_COST; }

        @Override
        public void setMaximumRepairCost(int levels) {
            if (levels < 0) throw new IllegalArgumentException("Maximum repair cost must be positive (or 0)");
            if (levels != AnvilState.MAX_REPAIR_COST) throw anvilFixed("AnvilInventory#setMaximumRepairCost");
        }

        AnvilState state() { return state; }
    }

    /** 양조대: 0~2 병, 3 재료, 4 연료. */
    static final class Brewer extends SnapshotInventory implements org.bukkit.inventory.BrewerInventory {
        Brewer(List<org.domi.furnace.api.item.ItemStack> contents, int size, String key, org.bukkit.Location location) {
            super(InventoryType.BREWING, contents, size, key, i -> false, location);
        }

        @Override
        public ItemStack getIngredient() { return getItem(3); }

        @Override
        public void setIngredient(ItemStack ingredient) { setItem(3, ingredient); }

        @Override
        public ItemStack getFuel() { return getItem(4); }

        @Override
        public void setFuel(ItemStack fuel) { setItem(4, fuel); }

        @Override
        public org.bukkit.block.BrewingStand getHolder() { return null; }
    }

    /** 마법 부여대: 0 아이템, 1 청금석. */
    static final class Enchanting extends SnapshotInventory implements org.bukkit.inventory.EnchantingInventory {
        Enchanting(List<org.domi.furnace.api.item.ItemStack> contents, int size, String key) {
            super(InventoryType.ENCHANTING, contents, size, key);
        }

        @Override
        public void setItem(ItemStack item) { setItem(0, item); }

        @Override
        public ItemStack getItem() { return getItem(0); }

        @Override
        public void setSecondary(ItemStack item) { setItem(1, item); }

        @Override
        public ItemStack getSecondary() { return getItem(1); }
    }
}
