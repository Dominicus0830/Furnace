package org.bukkit.inventory;

/** 모루 칸: 0 첫째 재료, 1 둘째 재료, 2 결과. 비용·이름 값은 1.21 부터 {@link org.bukkit.inventory.view.AnvilView} 에 있다(여기 것은 옛 이름). */
public interface AnvilInventory extends Inventory {

    @Deprecated(forRemoval = true, since = "1.21")
    String getRenameText();

    @Deprecated(forRemoval = true, since = "1.21")
    int getRepairCostAmount();

    @Deprecated(forRemoval = true, since = "1.21")
    void setRepairCostAmount(int amount);

    @Deprecated(forRemoval = true, since = "1.21")
    int getRepairCost();

    @Deprecated(forRemoval = true, since = "1.21")
    void setRepairCost(int levels);

    @Deprecated(forRemoval = true, since = "1.21")
    int getMaximumRepairCost();

    @Deprecated(forRemoval = true, since = "1.21")
    void setMaximumRepairCost(int levels);

    default ItemStack getFirstItem() { return getItem(0); }

    default void setFirstItem(ItemStack firstItem) { setItem(0, firstItem); }

    default ItemStack getSecondItem() { return getItem(1); }

    default void setSecondItem(ItemStack secondItem) { setItem(1, secondItem); }

    default ItemStack getResult() { return getItem(2); }

    default void setResult(ItemStack result) { setItem(2, result); }
}
