package org.bukkit.craftbukkit.inventory;

import org.bukkit.inventory.ItemStack;

/** 작업대(3×3)·플레이어 2×2 제작 칸(Paper 와 같은 이름 — T-264): 칸 0 결과, 1.. 격자. */
public class CraftInventoryCrafting extends CraftInventory implements org.bukkit.inventory.CraftingInventory {
    private final org.bukkit.inventory.Recipe recipe;

    /** recipe: 이벤트가 알린 레시피(모르면 null). */
    public CraftInventoryCrafting(org.domi.furnace.api.inventory.Inventory handle, org.bukkit.event.inventory.InventoryType type,
            org.bukkit.inventory.Recipe recipe) {
        super(handle, type, () -> null);
        this.recipe = recipe;
    }

    @Override
    public ItemStack getResult() { return getItem(0); }   // Paper: 결과 칸 거울(그 자리 고침이 결과에 남는다)

    @Override
    public ItemStack[] getMatrix() {
        ItemStack[] m = new ItemStack[getSize() - 1];
        for (int i = 0; i < m.length; i++) m[i] = getItem(i + 1);   // 거울: 그 자리 쓰기는 UnsupportedFeature 로 드러난다
        return m;
    }

    @Override
    public void setResult(ItemStack newResult) { setItem(0, newResult); }

    @Override
    public void setMatrix(ItemStack[] contents) {
        if (contents.length > getSize() - 1) throw new IllegalArgumentException("Invalid inventory size (" + contents.length + "); expected " + (getSize() - 1) + " or less");
        for (int i = 0; i < getSize() - 1; i++) setItem(i + 1, i < contents.length ? contents[i] : null);
    }

    @Override
    public org.bukkit.inventory.Recipe getRecipe() { return recipe; }
}
