// 큰 상자를 합친 54칸(Paper CraftInventoryDoubleChest, 바닐라 CompoundContainer): 칸 0..26 = 왼쪽(오른쪽 type 상자, 바닐라 DoubleBlockCombiner 의 첫째),
// 27..53 = 오른쪽. 양쪽은 그 칸 블록 엔티티의 살아 있는 인벤토리이고, 합친 것의 칸 읽기·쓰기는 해당 쪽으로 그대로 간다.
package org.domi.furnace.compat.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.DoubleChestInventory;

public final class CompatDoubleChestInventory extends CompatInventory implements DoubleChestInventory {

    private final CompatInventory left;
    private final CompatInventory right;

    private CompatDoubleChestInventory(CompatInventory left, CompatInventory right) {
        super(new Joined(left.handle, right.handle), org.bukkit.event.inventory.InventoryType.CHEST, () -> null);
        this.left = left;
        this.right = right;
    }

    /** 두 반쪽(살아 있는 인벤토리)을 합친다. */
    public static CompatDoubleChestInventory of(Inventory left, Inventory right) {
        return new CompatDoubleChestInventory((CompatInventory) left, (CompatInventory) right);
    }

    @Override
    public Inventory getLeftSide() { return left; }

    @Override
    public Inventory getRightSide() { return right; }

    @Override
    public org.bukkit.block.DoubleChest getHolder() { return new org.bukkit.block.DoubleChest(this); }

    @Override
    public org.bukkit.block.DoubleChest getHolder(boolean useSnapshot) { return getHolder(); }

    /** Paper: 두 칸 위치의 가운데. */
    @Override
    public org.bukkit.Location getLocation() {
        org.bukkit.Location a = left.getLocation(), b = right.getLocation();
        if (a == null || b == null) return null;
        return a.add(b).multiply(0.5);
    }

    /** 두 Furnace 인벤토리를 이어 붙인 칸. */
    private record Joined(org.domi.furnace.api.inventory.Inventory left, org.domi.furnace.api.inventory.Inventory right)
            implements org.domi.furnace.api.inventory.Inventory {

        @Override
        public String type() { return left.type(); }

        @Override
        public int size() { return left.size() + right.size(); }

        @Override
        public CompletableFuture<org.domi.furnace.api.item.ItemStack> get(int slot) {
            if (slot < 0 || slot >= size()) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size()));
            return slot < left.size() ? left.get(slot) : right.get(slot - left.size());
        }

        @Override
        public CompletableFuture<Void> set(int slot, org.domi.furnace.api.item.ItemStack item) {
            if (slot < 0 || slot >= size()) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size()));
            return slot < left.size() ? left.set(slot, item) : right.set(slot - left.size(), item);
        }

        @Override
        public CompletableFuture<Boolean> update(int slot, java.util.function.UnaryOperator<org.domi.furnace.api.item.ItemStack> fn) {
            if (slot < 0 || slot >= size()) return CompletableFuture.failedFuture(new IndexOutOfBoundsException("Index " + slot + " out of bounds for length " + size()));
            return slot < left.size() ? left.update(slot, fn) : right.update(slot - left.size(), fn);
        }

        @Override
        public CompletableFuture<List<org.domi.furnace.api.item.ItemStack>> contents() {
            return left.contents().thenCombine(right.contents(), (a, b) -> {
                List<org.domi.furnace.api.item.ItemStack> out = new ArrayList<>(a.size() + b.size());
                out.addAll(a);
                out.addAll(b);
                return out;
            });
        }
    }
}
