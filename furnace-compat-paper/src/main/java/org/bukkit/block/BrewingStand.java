package org.bukkit.block;

import org.bukkit.inventory.BrewerInventory;

/** Paper 1.21.1 BrewingStand: 양조대 블록 상태. */
public interface BrewingStand extends Container {

    int getBrewingTime();

    void setBrewingTime(int brewTime);

    /** Paper: 이번 양조에 드는 전체 시간(기본 400). */
    void setRecipeBrewTime(int recipeBrewTime);

    int getRecipeBrewTime();

    int getFuelLevel();

    void setFuelLevel(int level);

    @Override
    BrewerInventory getInventory();

    @Override
    BrewerInventory getSnapshotInventory();
}
