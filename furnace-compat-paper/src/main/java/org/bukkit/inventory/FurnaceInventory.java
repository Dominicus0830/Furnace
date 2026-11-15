package org.bukkit.inventory;

import org.bukkit.block.Furnace;

/** Paper 1.21.1 FurnaceInventory: 0 굽는 것, 1 연료, 2 결과. */
public interface FurnaceInventory extends Inventory {

    ItemStack getResult();

    ItemStack getFuel();

    ItemStack getSmelting();

    void setFuel(ItemStack stack);

    void setResult(ItemStack stack);

    void setSmelting(ItemStack stack);

    /** Paper: 화로 연료인가(비었으면 거짓). */
    boolean isFuel(ItemStack item);

    /** Paper: 이 화로 종류의 레시피로 구울 수 있는가(비었으면 거짓). */
    boolean canSmelt(ItemStack item);

    @Override
    Furnace getHolder();
}
