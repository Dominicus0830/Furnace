// 이벤트가 주는 창의 레시피와 모루 상태. 창 종류별 위쪽 인벤토리는 Paper 와 같은 이름(CraftInventoryCrafting·CraftInventoryAnvil·
// CraftInventoryBrewer·CraftInventoryFurnace·CraftInventoryEnchanting)으로 SnapshotInventory 의 사본 칸 위에 만든다(T-264 P2b).
package org.domi.furnace.compat.paper;

import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

public final class EventInventories {

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

    /** 모루 결과를 정하는 순간의 값(PrepareAnvilEvent) — 다른 이벤트의 모루 창은 이 값을 모른다(null). */
    public static final class AnvilState {
        /** 바닐라 모루의 최대 비용(이 값 이상이면 생존 모드에서 "너무 비쌈"). */
        public static final int MAX_REPAIR_COST = 40;

        public final String renameText;
        public int repairCost;
        public int maximumRepairCost;

        public AnvilState(String renameText, int repairCost, int maximumRepairCost) {
            this.renameText = renameText;
            this.repairCost = repairCost;
            this.maximumRepairCost = maximumRepairCost;
        }
    }

    public static UnsupportedFeature anvilUnknown(String what) {
        return new UnsupportedFeature(what, "anvil cost and name are only exposed while Furnace decides the anvil result (PrepareAnvilEvent)");
    }

    public static UnsupportedFeature anvilFixed(String what) {
        return new UnsupportedFeature(what, "the Furnace anvil event cannot change the number of materials used or lift the material level limit");
    }
}
