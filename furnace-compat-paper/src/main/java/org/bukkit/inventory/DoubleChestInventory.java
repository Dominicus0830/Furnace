package org.bukkit.inventory;

/** Paper 1.21.1 DoubleChestInventory: 큰 상자를 합친 54칸(왼쪽 = 칸 0..26, 오른쪽 = 27..53). */
public interface DoubleChestInventory extends Inventory {

    Inventory getLeftSide();

    Inventory getRightSide();

    @Override
    org.bukkit.block.DoubleChest getHolder();
}
