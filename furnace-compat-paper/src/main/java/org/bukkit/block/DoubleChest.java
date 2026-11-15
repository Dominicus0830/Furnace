package org.bukkit.block;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Paper 1.21.1 DoubleChest: 큰 상자 인벤토리의 주인(블록 상태가 아니다). 양쪽 주인은 각 반쪽 인벤토리의 주인(그 칸의 Chest 상태). */
public class DoubleChest implements InventoryHolder {

    private final DoubleChestInventory inventory;

    public DoubleChest(DoubleChestInventory chest) { inventory = chest; }

    @Override
    public Inventory getInventory() { return inventory; }

    public InventoryHolder getLeftSide() { return inventory.getLeftSide().getHolder(); }

    public InventoryHolder getRightSide() { return inventory.getRightSide().getHolder(); }

    public InventoryHolder getLeftSide(boolean useSnapshot) { return inventory.getLeftSide().getHolder(useSnapshot); }

    public InventoryHolder getRightSide(boolean useSnapshot) { return inventory.getRightSide().getHolder(useSnapshot); }

    public Location getLocation() { return getInventory().getLocation(); }

    public World getWorld() { return getLocation().getWorld(); }

    public double getX() { return getLocation().getX(); }

    public double getY() { return getLocation().getY(); }

    public double getZ() { return getLocation().getZ(); }
}
