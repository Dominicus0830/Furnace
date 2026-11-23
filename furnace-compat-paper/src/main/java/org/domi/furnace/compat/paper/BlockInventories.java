// 블록 상태(Container·Chest 등)의 getInventory: 그 칸 블록 엔티티의 살아 있는 인벤토리(Paper CraftInventory(tileEntity)).
package org.domi.furnace.compat.paper;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.domi.furnace.api.world.BlockPos;
import org.bukkit.craftbukkit.inventory.CraftInventory;

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
        return wrap(inv, holder).locatedAt(new org.bukkit.Location(Compat.world(dimension), pos.x(), pos.y(), pos.z()));
    }

    /**
     * 블록 상태의 사본 인벤토리(Paper getSnapshotInventory = CraftInventory(스냅숏 블록 엔티티)): 메모리 안 칸이고, 고친 것은 그 상태의 update 때 실린다.
     * blockEntityType 은 블록 엔티티 종류 id(minecraft:chest 등) — 화로류·양조대는 Paper 처럼 FurnaceInventory·BrewerInventory.
     */
    public static Inventory snapshot(String blockEntityType, org.domi.furnace.api.nbt.NbtCompound blockEntity,
                                     java.util.function.Supplier<InventoryHolder> holder, org.bukkit.Location at) {
        int size = InventoryResultMappings.containerType(blockEntityType).getDefaultSize();
        var handle = SnapshotInventory.memory(blockEntityType, ItemNbtCodec.readItems(blockEntity, size), size);
        return wrap(handle, holder).locatedAt(at);
    }

    /** 사본 인벤토리의 지금 칸 → NBT Items(비지 않은 칸만) — 상태가 update 때 되쓸 때. */
    public static org.domi.furnace.api.nbt.NbtList itemsNbt(Inventory snapshot) {
        return ItemNbtCodec.writeItems(((CraftInventory) snapshot).handle.contents().join());
    }

    /** 종류에 맞는 Bukkit 인벤토리 형으로 감싼다(양조대·화로류는 하위 인터페이스). */
    static CraftInventory wrap(org.domi.furnace.api.inventory.Inventory inv, java.util.function.Supplier<InventoryHolder> holder) {
        var type = InventoryResultMappings.containerType(inv.type());
        if (type == org.bukkit.event.inventory.InventoryType.BREWING) return new org.bukkit.craftbukkit.inventory.CraftInventoryBrewer(inv, holder);
        var kind = cookingKind(type);
        if (kind != null) return new org.bukkit.craftbukkit.inventory.CraftInventoryFurnace(inv, type, kind, holder);
        return new CraftInventory(inv, type, holder);
    }

    /** 화로류 인벤토리 종류 → 레시피 종류(화로류가 아니면 null). */
    static org.domi.furnace.api.recipe.Recipe.CookingKind cookingKind(org.bukkit.event.inventory.InventoryType type) {
        return switch (type) {
            case FURNACE -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMELTING;
            case BLAST_FURNACE -> org.domi.furnace.api.recipe.Recipe.CookingKind.BLASTING;
            case SMOKER -> org.domi.furnace.api.recipe.Recipe.CookingKind.SMOKING;
            default -> null;
        };
    }

    /** Paper CraftInventoryFurnace#isFuel: 비었으면 거짓, 아니면 바닐라 연료 표. */
    public static boolean isFuel(org.bukkit.inventory.ItemStack item) {
        return item != null && !item.getType().isAir() && Compat.furnace().gameRegistries().fuelBurnTime(item.getType().getKey().toString()) > 0;
    }

    /** Paper CraftInventoryFurnace#canSmelt: RecipeManager.getRecipeFor(이 화로의 레시피 종류, 아이템) — 서버 화로가 쓰는 조회 그대로. */
    public static boolean canSmelt(org.domi.furnace.api.recipe.Recipe.CookingKind kind, org.bukkit.inventory.ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return Compat.furnace().recipes().canCook(kind, CompatItems.toFurnace(item));
    }
}
