package org.bukkit.block;

import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.InventoryHolder;

/** 양조대 블록 상태(서명만 — Furnace 호환 계층은 아직 블록 상태 구현을 만들지 않는다, {@link BlockState} 참고). */
public interface BrewingStand extends BlockState, InventoryHolder {

    int getBrewingTime();

    void setBrewingTime(int brewTime);

    int getFuelLevel();

    void setFuelLevel(int level);

    @Override
    BrewerInventory getInventory();

    BrewerInventory getSnapshotInventory();
}
