// 큰 상자를 합친 54칸(Paper CraftInventoryDoubleChest, 바닐라 CompoundContainer): 칸 0..26 = 왼쪽(오른쪽 type 상자, 바닐라 DoubleBlockCombiner 의 첫째),
// 27..53 = 오른쪽. 양쪽은 그 칸 블록 엔티티의 살아 있는 인벤토리이고, 합친 것의 칸 읽기·쓰기는 해당 쪽으로 그대로 간다.
package org.bukkit.craftbukkit.inventory;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.DoubleChestInventory;
import org.domi.furnace.compat.paper.InventoryParts;

public class CraftInventoryDoubleChest extends CraftInventory implements DoubleChestInventory {

    private final CraftInventory left;
    private final CraftInventory right;

    private CraftInventoryDoubleChest(CraftInventory left, CraftInventory right) {
        super(new InventoryParts.Joined(left.handle, right.handle), org.bukkit.event.inventory.InventoryType.CHEST, () -> null);
        this.left = left;
        this.right = right;
    }

    /** 두 반쪽(살아 있는 인벤토리)을 합친다. */
    public static CraftInventoryDoubleChest of(Inventory left, Inventory right) {
        return new CraftInventoryDoubleChest((CraftInventory) left, (CraftInventory) right);
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
}
