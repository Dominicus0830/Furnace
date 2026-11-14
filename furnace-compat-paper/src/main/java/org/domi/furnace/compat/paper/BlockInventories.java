// 블록 상태(Container·Chest 등)의 getInventory: 그 칸 블록 엔티티의 살아 있는 인벤토리(Paper CraftInventory(tileEntity)).
package org.domi.furnace.compat.paper;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.domi.furnace.api.world.BlockPos;

public final class BlockInventories {

    private BlockInventories() {}

    /**
     * 그 칸의 살아 있는 용기 인벤토리 — 소유 스레드에서(아니면 ThreadOwnershipException). 읽기·쓰기는 그 스레드에서 곧바로 끝난다.
     * 칸이 없는 블록 엔티티(Furnace 가 아직 용기로 다루지 않는 것)면 null. getHolder() 는 Paper 처럼 그 칸의 새 블록 상태.
     */
    public static Inventory live(String dimension, BlockPos pos) {
        var inv = Compat.furnace().world(dimension).inventoryAtNow(pos).orElse(null);
        if (inv == null) return null;
        java.util.function.Supplier<InventoryHolder> holder = () -> {
            org.bukkit.block.BlockState s = Compat.block(dimension, pos).getState();
            return s instanceof InventoryHolder h ? h : null;
        };
        var type = InventoryResultMappings.containerType(inv.type());
        if (type == org.bukkit.event.inventory.InventoryType.BREWING) return new Brewer(inv, holder);
        var kind = switch (type) {
            case FURNACE -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMELTING;
            case BLAST_FURNACE -> org.domi.furnace.api.recipe.Recipe.CookingKind.BLASTING;
            case SMOKER -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMOKING;
            default -> null;
        };
        if (kind != null) return new Furnace(inv, type, kind, holder);
        return new CompatInventory(inv, type, holder);
    }

    /** 양조대의 살아 있는 인벤토리(Paper CraftInventoryBrewer): 0~2 병, 3 재료, 4 연료. */
    static final class Brewer extends CompatInventory implements org.bukkit.inventory.BrewerInventory {
        Brewer(org.domi.furnace.api.inventory.Inventory handle, java.util.function.Supplier<InventoryHolder> holder) {
            super(handle, org.bukkit.event.inventory.InventoryType.BREWING, holder);
        }

        @Override
        public org.bukkit.inventory.ItemStack getIngredient() { return getItem(3); }

        @Override
        public void setIngredient(org.bukkit.inventory.ItemStack ingredient) { setItem(3, ingredient); }

        @Override
        public org.bukkit.inventory.ItemStack getFuel() { return getItem(4); }

        @Override
        public void setFuel(org.bukkit.inventory.ItemStack fuel) { setItem(4, fuel); }

        @Override
        public org.bukkit.block.BrewingStand getHolder() {
            return super.getHolder() instanceof org.bukkit.block.BrewingStand b ? b : null;
        }
    }

    /** 화로류의 살아 있는 인벤토리(Paper CraftInventoryFurnace): 0 굽는 것, 1 연료, 2 결과. */
    static final class Furnace extends CompatInventory implements org.bukkit.inventory.FurnaceInventory {
        private final org.domi.furnace.api.recipe.Recipe.CookingKind kind;

        Furnace(org.domi.furnace.api.inventory.Inventory handle, org.bukkit.event.inventory.InventoryType type,
                org.domi.furnace.api.recipe.Recipe.CookingKind kind, java.util.function.Supplier<InventoryHolder> holder) {
            super(handle, type, holder);
            this.kind = kind;
        }

        @Override
        public org.bukkit.inventory.ItemStack getResult() { return getItem(2); }

        @Override
        public org.bukkit.inventory.ItemStack getFuel() { return getItem(1); }

        @Override
        public org.bukkit.inventory.ItemStack getSmelting() { return getItem(0); }

        @Override
        public void setFuel(org.bukkit.inventory.ItemStack stack) { setItem(1, stack); }

        @Override
        public void setResult(org.bukkit.inventory.ItemStack stack) { setItem(2, stack); }

        @Override
        public void setSmelting(org.bukkit.inventory.ItemStack stack) { setItem(0, stack); }

        @Override
        public boolean isFuel(org.bukkit.inventory.ItemStack item) {
            return item != null && !item.getType().isAir() && Compat.furnace().gameRegistries().fuelBurnTime(item.getType().getKey().toString()) > 0;
        }

        /** Paper: 이 화로 종류의 레시피 가운데 재료가 맞는 것이 있는가(레시피 표 = 데이터 팩 + 플러그인 레시피). */
        @Override
        public boolean canSmelt(org.bukkit.inventory.ItemStack item) {
            if (item == null || item.getType().isAir()) return false;
            String id = item.getType().getKey().toString();
            var recipes = Compat.furnace().recipes();
            for (String key : recipes.keys()) {
                if (recipes.get(key).orElse(null) instanceof org.domi.furnace.api.recipe.Recipe.Cooking c && c.kind() == kind && matches(c.ingredient(), id)) return true;
            }
            return false;
        }

        private static boolean matches(org.domi.furnace.api.recipe.RecipeChoice choice, String item) {
            return switch (choice) {
                case org.domi.furnace.api.recipe.RecipeChoice.Items i -> i.items().contains(item);
                case org.domi.furnace.api.recipe.RecipeChoice.Tag t -> Compat.furnace().gameRegistries().isTagged("minecraft:item", t.tag(), item);
                case org.domi.furnace.api.recipe.RecipeChoice.Any a -> a.choices().stream().anyMatch(c -> matches(c, item));
            };
        }

        @Override
        public org.bukkit.block.Furnace getHolder() {
            return super.getHolder() instanceof org.bukkit.block.Furnace f ? f : null;
        }
    }
}
