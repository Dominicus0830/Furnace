package org.bukkit.inventory;

/** 작업대(3×3)·플레이어 인벤토리(2×2) 제작 칸. 칸 0 이 결과, 1.. 이 격자. */
public interface CraftingInventory extends Inventory {

    /** 결과 칸(비었으면 null). */
    ItemStack getResult();

    /** 격자 칸들(빈 칸은 null). */
    ItemStack[] getMatrix();

    void setResult(ItemStack newResult);

    void setMatrix(ItemStack[] contents);

    /** 지금 격자에 맞는 레시피(없거나 모르면 null). */
    Recipe getRecipe();
}
