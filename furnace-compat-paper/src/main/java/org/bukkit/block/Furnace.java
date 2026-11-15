package org.bukkit.block;

import org.bukkit.inventory.FurnaceInventory;

/**
 * Paper 1.21.1 Furnace(화로·용광로·훈연기 블록 상태). 아직 없는 것: getRecipesUsed·setRecipeUsedCount·setRecipesUsed(CookingRecipe 형이 없다).
 */
public interface Furnace extends Container {

    short getBurnTime();

    void setBurnTime(short burnTime);

    short getCookTime();

    void setCookTime(short cookTime);

    int getCookTimeTotal();

    void setCookTimeTotal(int cookTimeTotal);

    double getCookSpeedMultiplier();

    void setCookSpeedMultiplier(double multiplier);

    int getRecipeUsedCount(org.bukkit.NamespacedKey furnaceRecipe);

    boolean hasRecipeUsedCount(org.bukkit.NamespacedKey furnaceRecipe);

    @Override
    FurnaceInventory getInventory();

    @Override
    FurnaceInventory getSnapshotInventory();
}
