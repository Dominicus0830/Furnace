package org.bukkit.inventory;

/** 양조대 칸: 0~2 병, 3 재료, 4 연료. */
public interface BrewerInventory extends Inventory {

    ItemStack getIngredient();

    void setIngredient(ItemStack ingredient);

    ItemStack getFuel();

    void setFuel(ItemStack fuel);

    /** 그 양조대(Furnace: 블록 상태 구현이 없어 null). */
    @Override
    org.bukkit.block.BrewingStand getHolder();
}
